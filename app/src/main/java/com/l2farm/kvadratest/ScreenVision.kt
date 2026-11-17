package com.l2farm.kvadratest

import android.media.Image
import kotlin.math.abs

data class VisionResult(
    val observation: FarmObservation,
    val diagnostics: String
)

object ScreenVision {
    // Uses actual image dimensions and tests two coordinate mappings.
    // UI readings are only reported when enough bar-colored pixels are present.
    fun inspect(image: Image): VisionResult {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val w = image.width
        val h = image.height
        val stride = plane.rowStride
        val pixelStride = plane.pixelStride
        fun pixel(x: Int, y: Int): IntArray? {
            if (x !in 0 until w || y !in 0 until h) return null
            val offset = y.toLong() * stride + x.toLong() * pixelStride
            if (offset + 2 >= buffer.limit() || offset < 0) return null
            val p = offset.toInt()
            return intArrayOf(buffer.get(p).toInt() and 255,buffer.get(p+1).toInt() and 255,buffer.get(p+2).toInt() and 255)
        }
        fun sample(x: Int,y: Int, mode: Int): IntArray? {
            val scale = if (mode == 0) w / 1600.0 else minOf(w / 1600.0,h / 960.0)
            val dx = if (mode == 0) 0.0 else (w - 1600 * scale) / 2.0
            val dy = if (mode == 0) 0.0 else (h - 960 * scale) / 2.0
            val sx = (x * scale + dx).toInt()
            val sy = (if (mode == 0) y * h / 960.0 else y * scale + dy).toInt()
            return pixel(sx,sy)
        }
        fun isHp(c:IntArray) = c[0] > 100 && c[0] > c[1] * 1.35 && c[0] > c[2] * 1.5
        fun isMp(c:IntArray) = c[2] > 75 && c[2] > c[0] * 1.25 && c[2] > c[1] * 1.08
        fun score(y:Int,mode:Int,test:(IntArray)->Boolean):Pair<Int,Int> {
            var hits=0
            var total=0
            for(x in 72..327 step 4) {
                val c=sample(x,y,mode) ?: continue
                total++
                if(test(c)) hits++
            }
            return Pair(hits,total)
        }
        var bestMode=0
        var best=-1
        for(mode in 0..1) {
            val hp=score(98,mode,::isHp).first
            val mp=score(121,mode,::isMp).first
            if(hp+mp>best) { best=hp+mp; bestMode=mode }
        }
        val (hpHits,hpTotal)=score(98,bestMode,::isHp)
        val (mpHits,mpTotal)=score(121,bestMode,::isMp)
        // Require multiple colored pixels before treating a bar as detected.
        val hp=if(hpHits>=8 && hpTotal>0) hpHits*100/hpTotal else null
        val mp=if(mpHits>=8 && mpTotal>0) mpHits*100/mpTotal else null
        var red=0
        for(x in 600..825 step 10) {
            val c=sample(x,58,bestMode)
            if(c!=null && isHp(c)) red++
        }
        var colorful=0
        var checked=0
        var dark=0
        for(y in 0 until h step (h/20).coerceAtLeast(1)) {
            for(x in 0 until w step (w/32).coerceAtLeast(1)) {
                val c=pixel(x,y) ?: continue
                checked++
                if(maxOf(c[0],c[1],c[2])-minOf(c[0],c[1],c[2])>35) colorful++
                if(maxOf(c[0],c[1],c[2])<30) dark++
            }
        }
        val diagnostics="mode=$bestMode hpPixels=$hpHits/$hpTotal mpPixels=$mpHits/$mpTotal redTarget=$red colors=$colorful/$checked dark=$dark/$checked"
        return VisionResult(FarmObservation(hp,mp,red>=6,null),diagnostics)
    }
}
