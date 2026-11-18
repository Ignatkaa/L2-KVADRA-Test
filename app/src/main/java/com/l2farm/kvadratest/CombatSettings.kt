package com.l2farm.kvadratest

/**
 * Configurable time-boxed combat. Validation is repeated in the controller,
 * not only in the UI, so older saved preferences cannot bypass limits.
 * Character HP/MP remain managed by the game.
 */
data class CombatSettings(
    val durationMinutes: Int = 3,
    val tapIntervalMs: Long = 3500L
) {
    val durationMs: Long get() = durationMinutes.toLong() * 60_000L

    /**
     * Mathematical upper bound on gestures allowed by the session duration
     * and minimum gap. Unlike the obsolete 40-gesture cap, it does not end
     * a long session prematurely when targets remain available.
     */
    val maxGestures: Int get() =
        (durationMs / tapIntervalMs + 1).toInt()

    companion object {
        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 120
        const val MIN_INTERVAL_MS = 2000L
        const val MAX_INTERVAL_MS = 10000L
        const val INTERVAL_STEP_MS = 500L

        fun safe(durationMinutes: Int, tapIntervalMs: Long): CombatSettings {
            val minutes = durationMinutes.coerceIn(
                MIN_DURATION_MINUTES,MAX_DURATION_MINUTES
            )
            val clamped = tapIntervalMs.coerceIn(MIN_INTERVAL_MS,MAX_INTERVAL_MS)
            val rounded = MIN_INTERVAL_MS +
                ((clamped - MIN_INTERVAL_MS) / INTERVAL_STEP_MS) * INTERVAL_STEP_MS
            return CombatSettings(minutes,rounded)
        }
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
