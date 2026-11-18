package com.l2farm.kvadratest

import android.media.Image

data class VisionResult(
    val observation: FarmObservation,
    val diagnostics: String,
    val gameHud: Boolean
)

/**
 * Target-first L2Meta 2000x1110 calibration.
 * Character HP/MP are handled by the game's own consumable automation.
 * Observation only: no gestures are injected into L2Meta.
 */
object ScreenVision {
    fun inspect(image: Image): VisionResult {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        fun sx(x: Int): Int = (x.toDouble() * width / 2000.0)
            .toInt().coerceIn(0, width - 1)
        fun sy(y: Int): Int = (y.toDouble() * height / 1110.0)
            .toInt().coerceIn(0, height - 1)
        fun rgb(x: Int, y: Int): IntArray? {
            if (x !in 0 until width || y !in 0 until height) return null
            val index = y.toLong() * plane.rowStride + x.toLong() * plane.pixelStride
            if (index < 0 || index + 2 >= buffer.limit()) return null
            val i = index.toInt()
            return intArrayOf(
                buffer.get(i).toInt() and 255,
                buffer.get(i + 1).toInt() and 255,
                buffer.get(i + 2).toInt() and 255
            )
        }
        fun darkAt(x: Int, y: Int): Boolean {
            val c = rgb(sx(x), sy(y)) ?: return false
            return (c.maxOrNull() ?: 255) < 110
        }
        fun red(c: IntArray) =
            c[0] > 125 && c[0] > c[1] * 1.45 && c[0] > c[2] * 1.3

        val gameHud = listOf(95 to 38, 340 to 40, 820 to 38, 1195 to 40, 1540 to 42)
            .count { (x, y) -> darkAt(x, y) } >= 3

        // Target frame is the panel at the top centre of the L2Meta display.
        val targetPanel = gameHud &&
            listOf(807 to 22, 1210 to 23, 1205 to 99)
                .count { (x, y) -> darkAt(x, y) } >= 2

        // The visible target HP bar spans x=755..1078. Scan across neighbouring
        // rows to avoid losing the fill due to text overlay or motion.
        val targetBar = if (targetPanel) {
            BarAnalyzer.scan(
                sx(755), sx(1078), sy(72),
                (width / 2000).coerceAtLeast(1)
            ) { x, y -> rgb(x, y)?.let(::red) == true }
        } else null

        // Missing coloured pixels are UNKNOWN, never an invented 0% or "dead".
        val targetHp = targetBar?.percent
        val observation = FarmObservation(
            hp = null, mp = null,
            targetVisible = targetPanel, targetHp = targetHp
        )
        val diagnostics =
            "gameHud=$gameHud targetPanel=$targetPanel " +
            "target=${targetHp ?: "?"} " +
            "targetHits=${targetBar?.hits ?: 0} " +
            "targetRun=${targetBar?.continuity ?: 0} " +
            "targetScanY=${targetBar?.scanY ?: "?"} " +
            "targetCoverage=${targetBar?.confidence ?: 0}"
        return VisionResult(observation, diagnostics, gameHud)
    }
}
