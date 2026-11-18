package com.l2farm.kvadratest

/** A bar measurement in pixels; it does not infer a missing target's HP as zero. */
data class BarMeasurement(
    val percent: Int?,
    val hits: Int,
    val continuity: Int,
    val confidence: Int,
    val scanY: Int
)

/**
 * Finds a contiguous bar fill that starts close to the left border.
 * Scans neighbouring Y rows to avoid text/anti-aliasing crossing the chosen sample line.
 * The confidence field is contiguous fill coverage, not a probability.
 */
object BarAnalyzer {
    fun scan(
        start: Int,
        end: Int,
        centerY: Int,
        step: Int = 1,
        matches: (Int, Int) -> Boolean
    ): BarMeasurement {
        require(end > start && step > 0)
        val total = ((end - start) / step + 1).coerceAtLeast(1)
        val minRun = maxOf(6, total / 20)
        val maxLeadingGap = maxOf(6, (end - start) / 20)
        var best: BarMeasurement? = null
        var fallback = BarMeasurement(null, 0, 0, 0, centerY)
        for (dy in -8..8) {
            val y = centerY + dy
            var hits = 0
            var run = 0
            var runStart = -1
            var longest = 0
            var bestStart = -1
            var bestEnd = -1
            for (x in start..end step step) {
                val matchCount = (-1..1).count { offset -> matches(x, y + offset) }
                if (matchCount >= 2) {
                    hits++
                    if (run == 0) runStart = x
                    run++
                    if (run > longest) {
                        longest = run
                        bestStart = runStart
                        bestEnd = x
                    }
                } else {
                    run = 0
                }
            }
            if (hits > fallback.hits)
                fallback = BarMeasurement(null, hits, longest, 0, y)
            if (longest < minRun || bestStart - start > maxLeadingGap || bestStart < start) continue
            val pct = ((bestEnd - start) * 100.0 / (end - start))
                .toInt().coerceIn(0, 100)
            val candidate = BarMeasurement(pct, hits, longest, longest * 100 / total, y)
            if (best == null ||
                candidate.continuity > best.continuity ||
                (candidate.continuity == best.continuity &&
                    kotlin.math.abs(dy) < kotlin.math.abs(best.scanY - centerY))) {
                best = candidate
            }
        }
        return best ?: fallback
    }
}
