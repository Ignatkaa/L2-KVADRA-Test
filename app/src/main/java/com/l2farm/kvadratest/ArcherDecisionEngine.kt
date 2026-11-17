package com.l2farm.kvadratest

data class FarmObservation(val hp: Int?, val mp: Int?, val targetVisible: Boolean, val targetHp: Int?)
object ArcherDecisionEngine {
    fun advise(o: FarmObservation): String = when {
        o.hp == null -> "Не удалось определить HP — наблюдение"
        o.hp < 40 -> "HP ниже 40% — рекомендовано восстановление"
        o.mp != null && o.mp < 35 -> "MP ниже 35% — только обычная атака"
        o.targetVisible && o.targetHp != null -> "Цель обнаружена — обычная атака (рекомендация)"
        else -> "Цель не обнаружена — поиск (рекомендация)"
    }
}
