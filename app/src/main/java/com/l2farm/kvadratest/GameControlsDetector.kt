package com.l2farm.kvadratest

/** Coordinates of buttons in the captured screen coordinate system. */
data class GameControlPoint(val x: Int, val y: Int)
enum class GameControlsLayout { CLASSIC, RADIAL }
data class GameControls(
    val layout: GameControlsLayout,
    val target: GameControlPoint,
    val attack: GameControlPoint,
    val borderWidth: Int,
    val redSamples: Int
)

/**
 * Pixel-only detector calibrated on nine 2000 x 1110 L2Meta captures.
 *
 * Target: bright blue rectangular outline near the bottom right.
 * Attack: a large red circular button near the same outline.
 * The detector MUST find both shapes or return null. A missing detection
 * never falls back to guessed / stale touch coordinates.
 *
 * [packedRgb] returns 0xRRGGBB at an actual display pixel.
 */
object GameControlsDetector {
    private fun isBlue(value: Int?): Boolean {
        val v = value ?: return false
        val r = v shr 16 and 255
        val g = v shr 8 and 255
        val b = v and 255
        return r in 62..140 && g >= 100 && b >= 135 &&
            b - r >= 55 && b - g >= 30
    }

    private fun isRed(value: Int?): Boolean {
        val v = value ?: return false
        val r = v shr 16 and 255
        val g = v shr 8 and 255
        val b = v and 255
        return r in 80..200 && g <= 95 && b <= 90 &&
            r > g * 1.7 && r > b * 1.7
    }

    fun detect(width: Int, height: Int, packedRgb: (Int, Int) -> Int?): GameControls? {
        if (width < 800 || height < 440 || width <= height) return null
        val aspect = width.toDouble() / height
        if (kotlin.math.abs(aspect - 2000.0 / 1110.0) > 0.16) return null

        fun x(actual: Int) = (actual.toDouble() * width / 2000.0).toInt()
            .coerceIn(0, width - 1)
        fun y(actual: Int) = (actual.toDouble() * height / 1110.0).toInt()
            .coerceIn(0, height - 1)
        fun blue(px: Int, py: Int) = isBlue(packedRgb(x(px), y(py)))
        fun red(px: Int, py: Int) = isRed(packedRgb(x(px), y(py)))

        fun candidate(x1: Int, x2: Int, top: Int): GameControls? {
            val border = x2 - x1 + 1
            if (border !in 65..125) return null
            val mid = (x1 + x2) / 2
            if (mid !in 1650..1740) return null

            // A blue line alone is insufficient; both vertical edges must exist.
            for (edge in intArrayOf(x1, x2)) {
                val edgeHits = intArrayOf(12, 26, 44, 56).count { dy ->
                    (-3..3).any { dx -> blue(edge + dx, top + dy) }
                }
                if (edgeHits < 3) return null
            }
            val layout = if (top < 900) GameControlsLayout.CLASSIC
                else GameControlsLayout.RADIAL
            val area = if (layout == GameControlsLayout.CLASSIC)
                intArrayOf(1795, 1923, 793, 929)
            else intArrayOf(1737, 1890, 875, 1021)

            var hits = 0
            var sumX = 0L
            var sumY = 0L
            for (ry in area[2]..area[3] step 3) {
                for (rx in area[0]..area[1] step 3) {
                    if (red(rx, ry)) {
                        hits++
                        sumX += rx
                        sumY += ry
                    }
                }
            }
            if (hits < 200) return null
            val cx = (sumX / hits).toInt()
            val cy = (sumY / hits).toInt()
            if (cx - mid !in 35..220) return null
            if (layout == GameControlsLayout.CLASSIC && cy !in 808..908) return null
            if (layout == GameControlsLayout.RADIAL && cy !in 898..990) return null

            // Centre of blue rectangle is 38px below its upper edge in both layouts.
            return GameControls(
                layout,
                GameControlPoint(x(mid), y(top + 38)),
                GameControlPoint(x(cx), y(cy)),
                border, hits
            )
        }

        var best: GameControls? = null
        for (py in 670..1040 step 2) {
            var run = -1
            for (px in 1580..1791) {
                val matched = px < 1791 && blue(px, py)
                if (matched && run < 0) run = px
                if (!matched && run >= 0) {
                    val result = candidate(run, px - 1, py)
                    if (result != null && (best == null ||
                            result.redSamples + result.borderWidth * 4 >
                            best.redSamples + best.borderWidth * 4)) {
                        best = result
                    }
                    run = -1
                }
            }
        }
        return best
    }
}
