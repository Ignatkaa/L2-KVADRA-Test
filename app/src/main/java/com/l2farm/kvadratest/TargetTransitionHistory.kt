package com.l2farm.kvadratest

import java.util.ArrayDeque

/** In-memory chronology of meaningful target-state transitions, not per-frame spam. */
class TargetTransitionHistory(private val maxEvents: Int = 24) {
    init { require(maxEvents > 0) }

    private var previous: TargetAction? = null
    private val events = ArrayDeque<String>()

    fun reset() {
        previous = null
        events.clear()
    }

    /**
     * Returns true only when the state changes, so the UI can persist
     * the updated history without writing every analyzed frame.
     */
    fun record(
        frame: Int,
        elapsedMs: Long,
        decision: TargetDecision,
        panelVisible: Boolean
    ): Boolean {
        if (previous == decision.action) return false
        val before = previous?.name ?: "START"
        val after = decision.action.name
        val seconds = (elapsedMs.coerceAtLeast(0L) / 1000L)
        val hp = decision.targetHp?.toString() ?: "?"
        events.addLast(
            "t+${seconds}s · кадр #$frame · $before → $after " +
                "(панель=$panelVisible, HP цели=$hp%)"
        )
        previous = decision.action
        while (events.size > maxEvents) events.removeFirst()
        return true
    }

    fun asText(): String =
        if (events.isEmpty()) "Переходов пока нет"
        else events.joinToString("\n")

    fun count(): Int = events.size
}
