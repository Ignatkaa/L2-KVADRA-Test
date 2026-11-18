package com.l2farm.kvadratest

/** Read-only battle recommendations. This class never sends gestures to the game. */
enum class TargetAction {
    SEARCH, ACQUIRING, ATTACK, VERIFY_TARGET, TARGET_LOST, HUD_UNAVAILABLE
}

data class TargetDecision(
    val action: TargetAction,
    val targetHp: Int?,
    val consecutiveValidFrames: Int,
    val recommendation: String
)

/**
 * Debounces the target panel and HP readout before advising attacks.
 *
 * Missing red pixels cannot prove that the target is dead: when the
 * target panel remains visible, the safest outcome is VERIFY_TARGET.
 * A large rise in the HP bar may mean retargeting; confirm the next frame.
 */
class TargetCombatTracker {
    private var validFrames = 0
    private var missingFrames = 0
    private var lastHp: Int? = null
    private var hadConfirmedTarget = false
    private var lastConfirmedHp: Int? = null

    fun reset() {
        validFrames = 0
        missingFrames = 0
        lastHp = null
        hadConfirmedTarget = false
        lastConfirmedHp = null
    }

    fun update(
        gameHud: Boolean,
        panelVisible: Boolean,
        targetHp: Int?
    ): TargetDecision {
        if (!gameHud) {
            return TargetDecision(
                TargetAction.HUD_UNAVAILABLE, null, validFrames,
                "Игровой интерфейс не подтверждён — действий не предлагать"
            )
        }

        if (!panelVisible) {
            missingFrames++
            validFrames = 0
            lastHp = null
            if (missingFrames < 2) {
                return TargetDecision(
                    TargetAction.VERIFY_TARGET, null, 0,
                    "Панель цели пропала на одном кадре — проверить повторно"
                )
            }
            val lost = hadConfirmedTarget
            hadConfirmedTarget = false
            lastConfirmedHp = null
            return if (lost) TargetDecision(
                TargetAction.TARGET_LOST, null, 0,
                "Прежняя цель исчезла — выбрать следующую; гибель не подтверждена"
            ) else TargetDecision(
                TargetAction.SEARCH, null, 0,
                "Нет выбранной цели — рекомендован поиск противника"
            )
        }

        missingFrames = 0
        if (targetHp == null || targetHp <= 0) {
            validFrames = 0
            lastHp = null
            return TargetDecision(
                TargetAction.VERIFY_TARGET, targetHp, 0,
                "Цель выделена, но её HP не подтверждено — не атаковать вслепую"
            )
        }

        // An abrupt upward jump may be a different opponent selected
        // under the same name (e.g. several Ol Mahum Recruit).
        val switched = lastHp != null && targetHp > lastHp!! + 30
        if (switched) {
            validFrames = 0
            hadConfirmedTarget = false
        }
        lastHp = targetHp
        validFrames++
        if (validFrames < 2) {
            return TargetDecision(
                TargetAction.ACQUIRING, targetHp, validFrames,
                "Цель выделена ($targetHp%) — подтверждаю на следующем кадре"
            )
        }
        hadConfirmedTarget = true
        lastConfirmedHp = targetHp
        return TargetDecision(
            TargetAction.ATTACK, targetHp, validFrames,
            "Цель подтверждена ($targetHp%) — рекомендована обычная атака"
        )
    }
}
