package com.l2farm.kvadratest

import android.media.Image

data class VisionResult(val observation: FarmObservation, val diagnostics: String)

/** L2Meta 2000x1110 landscape calibration; observation only, no input injection. */
object ScreenVision {
    fun inspect(image: Image): VisionResult {
        val plane=image.planes[0]
        val buffer=plane.buffer
        val w=image.width
        val h=image.height
        fun sx(x:Int)=((x.toDouble()*w/2000.0).toInt()).coerceIn(0,w-1)
        fun sy(y:Int)=((y.toDouble()*h/1110.0).toInt()).coerceIn(0,h-1)
        fun rgb(x:Int,y:Int):IntArray? {
            if(x !in 0 until w || y !in 0 until h) return null
            val index=y.toLong()*plane.rowStride+x.toLong()*plane.pixelStride
            if(index<0 || index+2>=buffer.limit()) return null
            val i=index.toInt()
            return intArrayOf(buffer.get(i).toInt() and 255,
                buffer.get(i+1).toInt() and 255,buffer.get(i+2).toInt() and 255)
        }
        fun red(c:IntArray)=c[0]>125 && c[0]>c[1]*1.45 && c[0]>c[2]*1.3
        fun blue(c:IntArray)=c[2]>125 && c[2]>c[0]*1.4 && c[2]>c[1]*1.18
        fun gold(c:IntArray)=c[0]>125 && c[1]>75 && c[0]>c[2]*1.5 && c[1]>c[2]*1.4
        data class Bar(val percent:Int?,val hits:Int,val continuity:Int)
        fun bar(x1:Int,x2:Int,yy:Int,predicate:(IntArray)->Boolean):Bar {
            val start=sx(x1)
            val end=sx(x2)
            val step=(w/2000).coerceAtLeast(1)
            var hits=0
            var longest=0
            var current=0
            var last=-1
            for(xx in start..end step step) {
                val ok=listOf(-2,0,2).count { dy ->
                    rgb(xx,sy(yy)+dy)?.let(predicate)==true
                }>=2
                if(ok) {
                    hits++
                    current++
                    longest=maxOf(longest,current)
                    last=xx
                } else current=0
            }
            val total=((end-start)/step+1).coerceAtLeast(1)
            // Never infer a percentage from a few unrelated pixels.
            if(hits<total/15 || longest<total/25 || last<0) return Bar(null,hits,longest)
            val pct=((last-start)*100.0/(end-start).coerceAtLeast(1)).toInt().coerceIn(0,100)
            return Bar(pct,hits,longest)
        }
        // x=126..383 are the full player bars in the actual PNG (not 154..460).
        fun darkAt(x:Int,y:Int):Boolean {
            val c=rgb(sx(x),sy(y)) ?: return false
            return (c.maxOrNull() ?: 255)<110
        }
        val gameHud=listOf(95 to 38,340 to 40,820 to 38,1195 to 40,1540 to 42)
            .count { (x,y) -> darkAt(x,y) }>=3
        val cp=bar(128,382,92,::gold)
        val hp=bar(128,382,117,::red)
        val mp=bar(128,382,142,::blue)
        // Target bar extends from 816 to 1084; 35% HP appears near x=910.
        val target=bar(817,1082,68,::red)
        val panel=gameHud && cp.percent!=null && hp.percent!=null && mp.percent!=null
        val targetVisible=gameHud && listOf(807 to 22,1210 to 23,1205 to 99)
            .count { (x,y) -> darkAt(x,y) }>=2
        val targetPercent=if(targetVisible) (target.percent ?: 0) else null
        val obs=FarmObservation(if(panel) hp.percent else null,
            if(panel) mp.percent else null,targetVisible,
            targetPercent)
        val probe=listOf(92,117,142).joinToString(";") { yy ->
            val c=rgb(sx(180),sy(yy))
            "$yy:"+(c?.joinToString(",") ?: "?")
        }
        val diagnostics="gameHud=$gameHud panel=$panel cp=${cp.percent ?: "?"} hp=${hp.percent ?: "?"} mp=${mp.percent ?: "?"} target=${target.percent ?: "?"} hits=${cp.hits}/${hp.hits}/${mp.hits}/${target.hits} runs=${cp.continuity}/${hp.continuity}/${mp.continuity}/${target.continuity} rgb=$probe"
        return VisionResult(obs,diagnostics)
    }
}
