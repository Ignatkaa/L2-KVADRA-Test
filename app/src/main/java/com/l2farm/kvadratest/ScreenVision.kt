package com.l2farm.kvadratest

import android.media.Image

data class VisionResult(
    val observation: FarmObservation,
    val diagnostics: String,
    val gameHud: Boolean,
    val controls: GameControls?
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

        // Stable dark HUD anchors on all seven supplied L2Meta PNGs.
        // Avoid background-dependent pixels from the old recognizer.
        val gameHud = listOf(120 to 38, 340 to 40, 1835 to 38)
            .count { (x, y) -> darkAt(x, y) } >= 2

        fun packedRgb(px: Int, py: Int): Int? {
            if (px !in 0 until width || py !in 0 until height) return null
            val index = py.toLong() * plane.rowStride + px.toLong() * plane.pixelStride
            if (index < 0 || index + 2 >= buffer.limit()) return null
            val i = index.toInt()
            val r = buffer.get(i).toInt() and 255
            val g = buffer.get(i + 1).toInt() and 255
            val b = buffer.get(i + 2).toInt() and 255
            return (r shl 16) or (g shl 8) or b
        }
        val controls = if (gameHud)
            GameControlsDetector.detect(width, height, ::packedRgb)
        else null

        // Target frame is the panel at the top centre of the L2Meta display.
        val targetPanel = gameHud &&
            listOf(807 to 22, 1210 to 23, 1205 to 99)
                .count { (x, y) -> darkAt(x, y) } >= 2

        // Two observed positions of the red target HP strip:
        // (755..1078) Ol Mahum and (980..1304) Hot Springs.
        // Unknown fill remains null; it never means a dead target.
        val targetReading = if (targetPanel) {
            TargetBarDetector.detect(width, height) { x, y ->
                rgb(x, y)?.let(::red) == true
            }
        } else null
        val targetHp = targetReading?.percent
        val observation = FarmObservation(
            hp = null, mp = null,
            targetVisible = targetPanel, targetHp = targetHp
        )
        val measured = targetReading?.let {
            if (it.position == TargetBarPosition.RIGHT_980) it.secondary
            else it.primary
        }
        val diagnostics =
            "gameHud=$gameHud targetPanel=$targetPanel " +
            "target=${targetHp ?: "?"} " +
            "targetRegion=${targetReading?.position ?: "?"} " +
            "targetHits=${measured?.hits ?: 0} " +
            "targetRun=${measured?.continuity ?: 0} " +
            "targetScanY=${measured?.scanY ?: "?"} " +
            "leftHits=${targetReading?.primary?.hits ?: 0} " +
            "rightHits=${targetReading?.secondary?.hits ?: 0} " +
            "controls=${controls?.layout ?: "?"} " +
            "targetButton=${controls?.target?.let { "${it.x},${it.y}" } ?: "?"} " +
            "attackButton=${controls?.attack?.let { "${it.x},${it.y}" } ?: "?"}"
        return VisionResult(observation, diagnostics, gameHud, controls)
    }
}
