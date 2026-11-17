package com.l2farm.kvadratest

import android.media.Image
import kotlin.math.abs

data class VisionResult(val observation: FarmObservation, val diagnostics: String)

object ScreenVision {
    private data class Run(val x:Int,val y:Int,val length:Int,val hits:Int)
    fun inspect(image: Image): VisionResult {
        val plane=image.planes[0]
        val buffer=plane.buffer
        val width=image.width
        val height=image.height
        val rowStride=plane.rowStride
        val pixelStride=plane.pixelStride
        fun rgb(x:Int,y:Int):IntArray? {
            if(x !in 0 until width || y !in 0 until height) return null
            val index=y.toLong()*rowStride+x.toLong()*pixelStride
            if(index<0 || index+2>=buffer.limit()) return null
            val i=index.toInt()
            return intArrayOf(buffer.get(i).toInt() and 255,buffer.get(i+1).toInt() and 255,buffer.get(i+2).toInt() and 255)
        }
        fun red(c:IntArray)=c[0]>105 && c[0]>c[1]*1.5 && c[0]>c[2]*1.3
        fun blue(c:IntArray)=c[2]>95 && c[2]>c[0]*1.3 && c[2]>c[1]*1.12
        // Scan only the upper-left HUD area, excluding the very top status bar.
        // A long red HP stripe should have a parallel blue MP stripe below it.
        val xLimit=(width*0.38).toInt()
        val yStart=(height*0.035).toInt()
        val yLimit=(height*0.28).toInt()
        val step=(width/800).coerceAtLeast(2)
        fun runs(y:Int,match:(IntArray)->Boolean):List<Run> {
            val found=mutableListOf<Run>()
            var start=-1
            var hits=0
            var last=-1
            for(x in 0 until xLimit step step) {
                val ok=rgb(x,y)?.let(match) ?: false
                if(ok) {
                    if(start<0) start=x
                    hits++
                    last=x
                } else if(start>=0) {
                    if(hits*step>=width*0.08) found.add(Run(start,y,last-start+step,hits))
                    start=-1
                    hits=0
                }
            }
            if(start>=0 && hits*step>=width*0.08) found.add(Run(start,y,last-start+step,hits))
            return found
        }
        var bestHp:Run?=null
        var bestMp:Run?=null
        var bestScore=-1
        for(y in yStart until yLimit step (height/300).coerceAtLeast(2)) {
            val hpRuns=runs(y,::red)
            if(hpRuns.isEmpty()) continue
            for(hp in hpRuns) {
                val gapMin=(height*0.012).toInt().coerceAtLeast(4)
                val gapMax=(height*0.055).toInt().coerceAtLeast(gapMin+1)
                for(my in (y+gapMin)..minOf(y+gapMax,yLimit) step (height/300).coerceAtLeast(2)) {
                    for(mp in runs(my,::blue)) {
                        val overlap=minOf(hp.x+hp.length,mp.x+mp.length)-maxOf(hp.x,mp.x)
                        if(overlap < minOf(hp.length,mp.length)*0.6) continue
                        val score=overlap + minOf(hp.length,mp.length) - abs(hp.length-mp.length)/2
                        if(score>bestScore) { bestScore=score; bestHp=hp; bestMp=mp }
                    }
                }
            }
        }
        // Finding a colored run is not sufficient to infer a percentage.
        // Bar fill must be calibrated against its frame boundaries first.
        val panel=bestHp!=null && bestMp!=null
        var colorful=0
        var checked=0
        var dark=0
        for(y in 0 until height step (height/20).coerceAtLeast(1)) {
            for(x in 0 until width step (width/32).coerceAtLeast(1)) {
                val c=rgb(x,y) ?: continue
                checked++
                if(maxOf(c[0],c[1],c[2])-minOf(c[0],c[1],c[2])>35) colorful++
                if(maxOf(c[0],c[1],c[2])<30) dark++
            }
        }
        val diagnostics="panel=$panel hpRun=${bestHp?.let { "${it.x},${it.y},${it.length}" } ?: "none"} mpRun=${bestMp?.let { "${it.x},${it.y},${it.length}" } ?: "none"} colors=$colorful/$checked dark=$dark/$checked"
        return VisionResult(FarmObservation(null,null,false,null),diagnostics)
    }
}
