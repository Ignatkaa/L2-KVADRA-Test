package com.l2farm.kvadratest

import android.media.Image
import java.nio.ByteBuffer

object ScreenVision {
    // Coordinates calibrated from a 1600x960 landscape L2Meta screenshot.
    // Returns null when bar colors do not match; this is heuristic, not OCR.
    fun inspect(image: Image): FarmObservation {
        val plane = image.planes[0]
        val b = plane.buffer
        val w = image.width
        val h = image.height
        val stride = plane.rowStride
        val pixel = plane.pixelStride
        fun rgb(x: Int, y: Int): IntArray? {
            val xx = (x.toLong() * w / 1600).toInt()
            val yy = (y.toLong() * h / 960).toInt()
            val i = yy.toLong() * stride + xx.toLong() * pixel
            if (xx !in 0 until w || yy !in 0 until h || i < 0 || i + 2 >= b.limit()) return null
            val p = i.toInt()
            return intArrayOf(b.get(p).toInt() and 255, b.get(p+1).toInt() and 255, b.get(p+2).toInt() and 255)
        }
        fun bar(y: Int, matches: (IntArray)->Boolean): Int? {
            var hits = 0
            var valid = 0
            for (x in 72..327 step 4) {
                val c = rgb(x,y) ?: continue
                valid++
                if (matches(c)) hits++
            }
            if (valid < 30 || hits == 0) return null
            return (hits * 100 / valid).coerceIn(0,100)
        }
        val hp = bar(98) { it[0] > it[1] * 1.35 && it[0] > it[2] * 1.5 && it[0] > 90 }
        val mp = bar(121) { it[2] > it[0] * 1.25 && it[2] > it[1] * 1.08 && it[2] > 75 }
        // Target panel normally occupies the top center. Detection is intentionally conservative.
        var red = 0
        for (x in 600..825 step 10) {
            val c = rgb(x, 58)
            if (c != null && c[0] > 120 && c[0] > c[1] * 1.4 && c[0] > c[2] * 1.4) red++
        }
        val target = red >= 6
        return FarmObservation(hp,mp,target,null)
    }
}
