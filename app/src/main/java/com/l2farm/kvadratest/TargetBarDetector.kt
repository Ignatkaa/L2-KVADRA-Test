package com.l2farm.kvadratest

/**
 * L2Meta moves the top-centre target panel between two horizontal positions.
 * Both positions were measured from real 2000x1110 game PNG captures.
 */
enum class TargetBarPosition { LEFT_755, RIGHT_980 }

data class TargetHpReading(
    val percent: Int?,
    val position: TargetBarPosition?,
    val primary: BarMeasurement,
    val secondary: BarMeasurement
)

/**
 * Reads the target fill, not character HP/MP. An absent or empty red region
 * yields null; it is never interpreted as confirmed 0% / a dead target.
 */
object TargetBarDetector {
    fun detect(
        width: Int,
        height: Int,
        matchesRed: (Int, Int) -> Boolean,
        preferredPosition: TargetBarPosition? = null
    ): TargetHpReading {
        require(width > 0 && height > 0)
        fun x(n: Int): Int = (n.toDouble() * width / 2000.0)
            .toInt().coerceIn(0, width-1)
        fun y(n: Int): Int = (n.toDouble() * height / 1110.0)
            .toInt().coerceIn(0, height-1)
        val step = (width / 2000).coerceAtLeast(1)
        val primary = BarAnalyzer.scan(x(755), x(1078), y(72), step, matchesRed)
        val secondary = BarAnalyzer.scan(x(980), x(1304), y(72), step, matchesRed)
        val leftValid = primary.percent != null
        val rightValid = secondary.percent != null
        val chooseLeft = when {
            preferredPosition == TargetBarPosition.LEFT_755 -> true
            preferredPosition == TargetBarPosition.RIGHT_980 -> false
            leftValid && !rightValid -> true
            !leftValid && rightValid -> false
            leftValid && rightValid ->
                primary.continuity >= secondary.continuity
            else -> true
        }
        val chosen = if (chooseLeft) primary else secondary
        val position = if (chosen.percent == null) null
            else if (chooseLeft) TargetBarPosition.LEFT_755
            else TargetBarPosition.RIGHT_980
        return TargetHpReading(chosen.percent, position, primary, secondary)
    }
}
