package com.l2farm.kvadratest

import android.media.Image

data class VisionResult(val observation: FarmObservation, val diagnostics: String)

/** L2Meta reference coordinate space: 2000 x 1110. No game input is generated. */
object ScreenVision {
    fun inspect(image: Image): VisionResult {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val w = image.width
        val h = image.height
        fun sx(x: Int) = (x.toDouble() * w / 2000.0).toInt().coerceIn(0, w - 1)
        fun sy(y: Int) = (y.toDouble() * h / 1110.0).toInt().coerceIn(0, h - 1)
        fun rgb(x: Int, y: Int): IntArray? {
            if (x !in 0 until w || y !in 0 until h) return null
            val offset = y.toLong() * plane.rowStride + x.toLong() * plane.pixelStride
            if (offset < 0 || offset + 2 >= buffer.limit()) return null
            val i = offset.toInt()
            return intArrayOf(buffer.get(i).toInt() and 255,
                buffer.get(i + 1).toInt() and 255, buffer.get(i + 2).toInt() and 255)
        }
        fun red(c: IntArray) = c[0] > 120 && c[0] > c[1] * 1.35 && c[0] > c[2] * 1.25
        fun blue(c: IntArray) = c[2] > 115 && c[2] > c[0] * 1.30 && c[2] > c[1] * 1.12
        fun gold(c: IntArray) = c[0] > 115 && c[1] > 65 && c[0] > c[2] * 1.35 && c[1] > c[2] * 1.25
        fun dark(x: Int, y: Int): Boolean {
            val c = rgb(sx(x), sy(y)) ?: return false
            return c.maxOrNull()!! < 110
        }
        data class Bar(val percent: Int?, val hits: Int, val run: Int, val y: Int)
        fun scan(x1: Int, x2: Int, centerY: Int, color: (IntArray) -> Boolean): Bar {
            val start = sx(x1)
            val end = sx(x2)
            val step = maxOf(1, w / 2000)
            var best = Bar(null, 0, 0, centerY)
            for (offset in -7..7 step 2) {
                val y = sy(centerY + offset)
                var hits = 0
                var run = 0
                var longest = 0
                var last = -1
                for (x in start..end step step) {
                    val matches = (-1..1).count { dy -> rgb(x, y + dy)?.let(color) == true }
                    if (matches >= 2) {
                        hits++
                        run++
                        longest = maxOf(longest, run)
                        last = x
                    } else run = 0
                }
                val total = ((end - start) / step + 1).coerceAtLeast(1)
                val valid = hits >= maxOf(8, total / 18) && longest >= maxOf(6, total / 30)
                val pct = if (valid && last >= 0)
                    ((last - start) * 100.0 / (end - start).coerceAtLeast(1)).toInt().coerceIn(0, 100)
                    else null
                val candidate = Bar(pct, hits, longest, centerY + offset)
                if (candidate.run > best.run || (candidate.run == best.run && candidate.hits > best.hits))
                    best = candidate
            }
            return best
        }
        val hud = listOf(95 to 38, 340 to 40, 820 to 38, 1195 to 40, 1540 to 42)
            .count { (x, y) -> dark(x, y) } >= 3
        val cp = scan(128, 382, 92, ::gold)
        val hp = scan(128, 382, 117, ::red)
        val mp = scan(128, 382, 142, ::blue)
        val target = scan(817, 1082, 68, ::red)
        val panel = hud && hp.percent != null && mp.percent != null
        val targetVisible = hud && listOf(807 to 22, 1210 to 23, 1205 to 99)
            .count { (x, y) -> dark(x, y) } >= 2
        val obs = FarmObservation(
            if (panel) hp.percent else null,
            if (panel) mp.percent else null,
            targetVisible,
            if (targetVisible) target.percent else null
        )
        val diagnostics = "gameHud=$hud panel=$panel cp=${cp.percent ?: "?"} hp=${hp.percent ?: "?"} mp=${mp.percent ?: "?"} target=${target.percent ?: "?"} " +
            "hits=${cp.hits}/${hp.hits}/${mp.hits}/${target.hits} " +
            "runs=${cp.run}/${hp.run}/${mp.run}/${target.run} " +
            "scanY=${cp.y}/${hp.y}/${mp.y}/${target.y}"
        return VisionResult(obs, diagnostics)
    }
}
