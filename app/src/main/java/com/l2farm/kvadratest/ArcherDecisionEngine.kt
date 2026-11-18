package com.l2farm.kvadratest

/** Kept for compatibility with the screen-recognition API. */
data class FarmObservation(
    val hp: Int?,
    val mp: Int?,
    val targetVisible: Boolean,
    val targetHp: Int?
)

/** Single-frame suggestion; use TargetCombatTracker for real capture sessions. */
object ArcherDecisionEngine {
    fun advise(o: FarmObservation): String = when {
        !o.targetVisible -> "Нет цели — рекомендован поиск"
        o.targetHp == null || o.targetHp <= 0 ->
            "Панель цели видна, HP неизвестно — требуется подтверждение"
        else -> "Цель видна (${o.targetHp}%) — атака после подтверждения нескольких кадров"
    }
}
