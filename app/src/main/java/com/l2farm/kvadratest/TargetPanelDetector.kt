package com.l2farm.kvadratest

/** The two observed L2Meta target-window positions at 2000x1110. */
data class TargetPanelEvidence(
    val position: TargetBarPosition,
    val topPercent: Int,
    val leftPercent: Int,
    val rightPercent: Int
) {
    val valid: Boolean
        get() = topPercent >= 85 && leftPercent >= 65 && rightPercent >= 65
    val score: Int get() = topPercent + leftPercent + rightPercent
}

data class TargetPanelReading(
    val position: TargetBarPosition?,
    val left: TargetPanelEvidence,
    val right: TargetPanelEvidence
) {
    val visible: Boolean get() = position != null
}

/**
 * Detects the *gold frame* of a selected target, not dark game scenery.
 *
 * A previous recognizer required 2/3 dark pixels around the expected target
 * area. Dark sky and trees falsely passed that check even when the target
 * window was absent, leaving auto-combat stuck with targetPanel=true / HP=?.
 *
 * This detector requires all three structural cues of the target window:
 * long horizontal gold border at y=14, and BOTH vertical gold sides.
 * On the 2000x1110 reference HUD the left edges are x=741 or x=967.
 * Lack of evidence is UNKNOWN/ABSENT, never an invented selected target.
 */
object TargetPanelDetector {
    private fun isGold(packed: Int?): Boolean {
        val c = packed ?: return false
        val r = c shr 16 and 255
        val g = c shr 8 and 255
        val b = c and 255
        return r >= 98 && g >= 75 && b < 110 &&
            r > g * 1.04 && g > b * 1.17 && r + g > 190
    }

    fun detect(
        width: Int,
        height: Int,
        packedRgb: (Int, Int) -> Int?
    ): TargetPanelReading {
        require(width > 0 && height > 0)
        fun sx(x: Int) = (x.toDouble() * width / 2000.0)
            .toInt().coerceIn(0, width - 1)
        fun sy(y: Int) = (y.toDouble() * height / 1110.0)
            .toInt().coerceIn(0, height - 1)
        fun gold(x: Int, y: Int): Boolean =
            isGold(packedRgb(sx(x), sy(y)))

        fun measure(x: Int, kind: TargetBarPosition): TargetPanelEvidence {
            var topCount = 0
            var topTotal = 0
            // Test a full-length gold frame, not a single gold background pixel.
            for (px in x + 28 until x + 488 step 5) {
                topTotal++
                if ((13..17).any { py -> gold(px, py) }) topCount++
            }
            var leftCount = 0
            var rightCount = 0
            var sideTotal = 0
            for (py in 25..110 step 7) {
                sideTotal++
                if ((x - 3..x + 6).any { px -> gold(px, py) })
                    leftCount++
                if ((x + 508..x + 519).any { px -> gold(px, py) })
                    rightCount++
            }
            return TargetPanelEvidence(
                kind,
                100 * topCount / topTotal.coerceAtLeast(1),
                100 * leftCount / sideTotal.coerceAtLeast(1),
                100 * rightCount / sideTotal.coerceAtLeast(1)
            )
        }
        val left = measure(741, TargetBarPosition.LEFT_755)
        val right = measure(967, TargetBarPosition.RIGHT_980)
        // A double-positive is ambiguous and therefore must not arm an attack.
        val found = when {
            left.valid && !right.valid -> left.position
            right.valid && !left.valid -> right.position
            else -> null
        }
        return TargetPanelReading(found, left, right)
    }
}
