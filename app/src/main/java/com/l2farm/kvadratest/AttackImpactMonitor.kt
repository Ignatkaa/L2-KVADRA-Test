package com.l2farm.kvadratest

/**
 * Passive, one-shot follow-up after the Android gesture callback confirms
 * dispatch. This does NOT trigger another touch or infer causality from damage.
 */
class AttackImpactMonitor {
    private var before: Int? = null
    private var startedAt: Long = 0L
    private var consecutiveLower = 0
    private var consecutiveMissing = 0
    private var active = false

    fun reset() {
        active = false
        before = null
        consecutiveLower = 0
        consecutiveMissing = 0
    }

    fun start(beforeHp: Int?, nowMillis: Long) {
        reset()
        if (beforeHp == null || beforeHp <= 0) return
        before = beforeHp
        startedAt = nowMillis
        active = true
    }

    fun isActive() = active

    /** Null while evidence collection continues; text on completion. */
    fun observe(nowMillis: Long, panelVisible: Boolean, hp: Int?): String? {
        if (!active) return null
        val initial = before ?: return null
        if (nowMillis - startedAt >= 20_000L) {
            reset()
            return "После касания «Атака» снижение HP цели не подтверждено за 20 секунд"
        }
        if (!panelVisible) {
            consecutiveMissing++
            if (consecutiveMissing >= 2) {
                reset()
                return "После касания «Атака» панель цели исчезла; причина неизвестна"
            }
        } else {
            consecutiveMissing = 0
        }
        // A single noisy HP reading cannot establish a real decrease.
        if (hp != null && hp >= 1 && hp <= initial - 2) {
            consecutiveLower++
            if (consecutiveLower >= 2) {
                reset()
                return "После касания «Атака» наблюдалось уменьшение HP цели: " +
                    "$initial% → $hp% (на двух кадрах); причинная связь не доказана"
            }
        } else {
            consecutiveLower = 0
        }
        return null
    }
}
