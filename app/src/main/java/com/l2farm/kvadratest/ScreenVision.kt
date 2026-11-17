package com.l2farm.kvadratest

import android.media.Image
import kotlin.math.abs

data class VisionResult(val observation: FarmObservation, val diagnostics: String)

/** v0.8: calibrated to a real 2000x1110 MediaProjection PNG, not a system screenshot.
 * No OCR. Percentages are experimental and only returned when the bar is recognizable.
 */
object ScreenVision {
    fun inspect(image: Image): VisionResult {
        val p=image.planes[0]
        val b=p.buffer
        val w=image.width
        val h=image.height
        val stride=p.rowStride
        val pixelStride=p.pixelStride
        fun rgb(x:Int,y:Int):IntArray? {
            if(x !in 0 until w || y !in 0 until h) return null
            val offset=y.toLong()*stride+x.toLong()*pixelStride
            if(offset<0 || offset+2>=b.limit()) return null
            val i=offset.toInt()
            return intArrayOf(b.get(i).toInt() and 255,b.get(i+1).toInt() and 255,b.get(i+2).toInt() and 255)
        }
        fun red(c:IntArray)=c[0]>100 && c[0]>c[1]*1.35 && c[0]>c[2]*1.3
        fun blue(c:IntArray)=c[2]>90 && c[2]>c[0]*1.25 && c[2]>c[1]*1.08
        fun gold(c:IntArray)=c[0]>100 && c[1]>65 && c[0]>c[2]*1.45 && c[1]>c[2]*1.2
        fun x(v:Int)=(v*w/2000.0).toInt()
        fun y(v:Int)=(v*h/1110.0).toInt()
        // Estimate game viewport edges from black side letterboxing.
        fun nonBlackAt(xx:Int):Boolean {
            val c=rgb(xx,y(500)) ?: return false
            return maxOf(c[0],c[1],c[2])>30
        }
        var left=0
        while(left<w/5 && !nonBlackAt(left)) left+=4
        var right=w-1
        while(right>w*4/5 && !nonBlackAt(right)) right-=4
        val viewportOk=left<w/5 && right>w*4/5
        // Pixel positions calibrated against the user's v0.7 PNG.
        // A few adjacent scanlines mitigate text overlays on bars.
        fun bar(start:Int,end:Int,cy:Int,predicate:(IntArray)->Boolean):Pair<Int?,Int> {
            var matched=0
            var total=0
            var first=-1
            var last=-1
            val increment=(w/1000).coerceAtLeast(1)
            for(xx in x(start)..x(end) step increment) {
                var votes=0
                for(dy in listOf(-3,0,3)) {
                    val c=rgb(xx,y(cy)+dy)
                    if(c!=null && predicate(c)) votes++
                }
                if(votes>=2) {
                    matched++
                    if(first<0) first=xx
                    last=xx
                }
                total++
            }
            if(matched<total/10 || first<0) return Pair(null,matched)
            // Measure fill relative to the known full bar width, not to the detected colored run.
            val pct=(100.0*(last-x(start)).coerceAtLeast(0)/(x(end)-x(start)).coerceAtLeast(1)).toInt().coerceIn(0,100)
            return Pair(pct,matched)
        }
        // The image is 2000x1110; HP/MP bars are in the upper-left panel.
        val (hpRaw,hpHits)=bar(154,460,113,::red)
        val (mpRaw,mpHits)=bar(154,460,141,::blue)
        val (cpRaw,cpHits)=bar(154,460,85,::gold)
        val panel=viewportOk && hpRaw!=null && mpRaw!=null && cpHits>=8
        // Target HP is at top center; absent target must not look like 0% HP.
        val (targetHp,targetHits)=bar(980,1300,82,::red)
        val target=viewportOk && targetHits>=12 && targetHp!=null
        val hp=if(panel) hpRaw else null
        val mp=if(panel) mpRaw else null
        val obs=FarmObservation(hp,mp,target,if(target) targetHp else null)
        val probes=listOf(85,113,141,165).joinToString(";") { yy ->
            val xx=x(300)
            val c=rgb(xx,y(yy))
            "$yy:"+(c?.joinToString(",") ?: "null")
        }
        // Broad scan helps distinguish a wrong row from incorrect channel interpretation.
        fun countAt(yy:Int, match:(IntArray)->Boolean):Int {
            var hits=0
            for(xx in x(100)..x(510) step 4) {
                if(rgb(xx,y(yy))?.let(match)==true) hits++
            }
            return hits
        }
        val scans=listOf(70,85,100,113,125,141,155,170).joinToString(";") { yy ->
            "$yy:${countAt(yy,::red)}/${countAt(yy,::blue)}"
        }
        val diag="viewport=$left..$right panel=$panel hpHits=$hpHits mpHits=$mpHits cpHits=$cpHits targetHits=$targetHits targetHP=${obs.targetHp ?: "?"}% rgb=$probes scanRB=$scans"
        return VisionResult(obs,diag)
    }
}
