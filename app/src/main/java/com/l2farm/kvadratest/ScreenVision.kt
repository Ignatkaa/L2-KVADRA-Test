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
        val step=(w/2000).coerceAtLeast(1)
        fun measure(x1:Int,x2:Int,centerY:Int,predicate:(IntArray)->Boolean):BarMeasurement =
            BarAnalyzer.scan(sx(x1),sx(x2),sy(centerY),step) { x,y ->
                rgb(x,y)?.let(predicate)==true
            }
        // x=126..383 are the full player bars in the actual PNG (not 154..460).
        fun darkAt(x:Int,y:Int):Boolean {
            val c=rgb(sx(x),sy(y)) ?: return false
            return (c.maxOrNull() ?: 255)<110
        }
        val gameHud=listOf(95 to 38,340 to 40,820 to 38,1195 to 40,1540 to 42)
            .count { (x,y) -> darkAt(x,y) }>=3
        val cp=measure(151,383,92,::gold)
        val hp=measure(151,383,117,::red)
        val mp=measure(151,383,142,::blue)
        // Calibrated on the game PNG: full target bar x=755..1078; fill starts near 756.
        val target=measure(755,1078,72,::red)
        val panel=gameHud && hp.percent!=null && mp.percent!=null
        val targetVisible=gameHud && listOf(807 to 22,1210 to 23,1205 to 99)
            .count { (x,y) -> darkAt(x,y) }>=2
        val targetPercent=if(targetVisible) target.percent else null
        val obs=FarmObservation(if(panel) hp.percent else null,
            if(panel) mp.percent else null,targetVisible,
            targetPercent)
        val probe=listOf(92,117,142).joinToString(";") { yy ->
            val c=rgb(sx(180),sy(yy))
            "$yy:"+(c?.joinToString(",") ?: "?")
        }
        val diagnostics="gameHud=$gameHud panel=$panel cp=${cp.percent ?: "?"} hp=${hp.percent ?: "?"} mp=${mp.percent ?: "?"} target=${target.percent ?: "?"} hits=${cp.hits}/${hp.hits}/${mp.hits}/${target.hits} runs=${cp.continuity}/${hp.continuity}/${mp.continuity}/${target.continuity} confidence=${cp.confidence}/${hp.confidence}/${mp.confidence}/${target.confidence} scanY=${cp.scanY}/${hp.scanY}/${mp.scanY}/${target.scanY} rgb=$probe"
        return VisionResult(obs,diagnostics)
    }
}
