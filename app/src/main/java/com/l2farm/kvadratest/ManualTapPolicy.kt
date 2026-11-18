package com.l2farm.kvadratest

/** Preconditions for ONE user-armed accessibility gesture, not an auto-farm loop. */
data class ManualTapEligibility(val allowed: Boolean, val reason: String)

object ManualTapPolicy {
    fun check(
        action: String,
        hudVisible: Boolean,
        controls: GameControls?,
        decision: TargetDecision
    ): ManualTapEligibility {
        if (!hudVisible) return ManualTapEligibility(false,
            "Игровой HUD не обнаружен — откройте L2Meta")
        if (controls == null) return ManualTapEligibility(false,
            "Кнопки «Цель» и «Атака» не распознаны — касание запрещено")
        return when (action) {
            // Explicit user test: 'Target' must also work when a mob is already selected.
            "TARGET" -> ManualTapEligibility(true,
                "Кнопка «Цель» обнаружена; одиночное тестовое нажатие разрешено")
            "ATTACK" -> if (decision.action == TargetAction.ATTACK &&
                (decision.targetHp ?: 0) > 0) {
                ManualTapEligibility(true,
                    "Живая цель подтверждена; одиночное тестовое нажатие «Атака» разрешено")
            } else {
                ManualTapEligibility(false,
                    "Для «Атака» нужна подтверждённая живая цель; состояние=${decision.action}")
            }
            else -> ManualTapEligibility(false,"Неизвестный тип тестового касания")
        }
    }
}
