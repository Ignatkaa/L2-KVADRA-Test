package com.l2farm.kvadratest

/**
 * User-adjustable settings with strict, testable safety caps. No unbounded mode.
 * Gameplay HP/MP are intentionally not considered here.
 */
data class CombatSettings(
    val durationMinutes: Int = 3,
    val tapIntervalMs: Long = 3500L
) {
    val durationMs: Long get() = durationMinutes * 60_000L
    val maxGestures: Int get() = (durationMinutes * 4).coerceAtMost(40)

    companion object {
        val DURATIONS = listOf(1, 3, 5, 10)
        val INTERVALS_MS = listOf(3500L, 5000L, 7000L, 10000L)

        fun safe(durationMinutes: Int, tapIntervalMs: Long) =
            CombatSettings(
                durationMinutes = durationMinutes.takeIf { it in DURATIONS } ?: 3,
                tapIntervalMs = tapIntervalMs.takeIf { it in INTERVALS_MS } ?: 3500L
            )
    }
}

data class CombatStatistics(
    val elapsedSeconds: Long,
    val targetTaps: Int,
    val attackTaps: Int,
    val confirmedSelections: Int,
    val damageObservations: Int,
    val lostPanels: Int,
    val attemptedGestures: Int,
    val gestureLimit: Int
)
