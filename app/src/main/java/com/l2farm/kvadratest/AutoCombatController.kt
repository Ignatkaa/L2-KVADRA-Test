package com.l2farm.kvadratest

/** Deliberately bounded autonomous gestures; only enabled by an explicit user start. */
enum class CombatMode { OFF, SEARCH, WAIT_TARGET, READY_ATTACK, WATCH_ATTACK, PAUSED }
enum class CombatTap { TARGET, ATTACK }
data class CombatStep(val tap: CombatTap?, val status: String, val mode: CombatMode)

/**
 * Closed-loop, fail-closed target/attack logic.
 *
 * This controller only proposes gestures. The Service must additionally check:
 * game foreground, live MediaProjection control coordinates, AccessibilityService,
 * physical display mapping, and that the user has not pressed STOP.
 */
class AutoCombatController {
    var mode: CombatMode = CombatMode.OFF
        private set
    var status: String = "Автобой выключен"
        private set
    var sentGestures = 0
        private set
    var targetsLost = 0
        private set
    private var startedAt = 0L
    private var lastObservedAt = 0L
    private var finishedAt: Long? = null
    private var settings = CombatSettings()
    private var successfulTargetTaps = 0
    private var successfulAttackTaps = 0
    private var confirmedSelections = 0
    private var damageObservations = 0
    private var lastGestureAt = Long.MIN_VALUE / 2
    private var phaseSince = 0L
    private var hudMissing = 0
    private var controlsStable = 0
    private var targetMissing = 0
    private var targetValid = 0
    private var waitingCallback = false
    private var expectedTap: CombatTap? = null
    private var gestureRequestedAt = 0L
    private var beforeHp: Int? = null
    private var lowerHpFrames = 0
    private var damageObserved = false
    private var unknownHpSince = 0L
    private var uiSince = 0L

    val running get() = mode != CombatMode.OFF && mode != CombatMode.PAUSED

    fun start(now: Long, requestedSettings: CombatSettings = CombatSettings()) {
        settings = CombatSettings.safe(requestedSettings.durationMinutes, requestedSettings.tapIntervalMs)
        mode = CombatMode.SEARCH
        status = "Автобой запущен вручную; ожидаю игровой HUD"
        startedAt = now
        lastObservedAt = now
        finishedAt = null
        phaseSince = now
        lastGestureAt = now - settings.tapIntervalMs
        successfulTargetTaps = 0
        successfulAttackTaps = 0
        confirmedSelections = 0
        damageObservations = 0
        sentGestures = 0
        targetsLost = 0
        hudMissing = 0
        controlsStable = 0
        targetMissing = 0
        targetValid = 0
        waitingCallback = false
        expectedTap = null
        beforeHp = null
        lowerHpFrames = 0
        damageObserved = false
        unknownHpSince = 0L
        uiSince = 0L
    }

    fun stop(reason: String = "Автобой остановлен вручную") {
        if (running && finishedAt == null) finishedAt = lastObservedAt
        mode = CombatMode.OFF
        status = reason
        waitingCallback = false
        expectedTap = null
    }

    fun pause(reason: String) {
        if (mode == CombatMode.OFF || mode == CombatMode.PAUSED) return
        finishedAt = lastObservedAt
        mode = CombatMode.PAUSED
        status = "ПАУЗА: $reason. Для продолжения необходим ручной запуск."
        waitingCallback = false
        expectedTap = null
    }

    fun timeCheck(now: Long) {
        if (!running) return
        lastObservedAt = now
        if (now - startedAt >= settings.durationMs)
            pause("достигнут предел ${settings.durationMinutes} мин")
        else if (waitingCallback && now - gestureRequestedAt > 4_000L)
            pause("Android не подтвердил жест за 4 секунды")
    }

    fun onOwnUi(now: Long) {
        timeCheck(now)
        if (!running) return
        if (uiSince == 0L) uiSince = now
        if (now - uiSince >= 12_000L)
            pause("приложение Hawkeye было открыто более 12 секунд")
    }

    /** Called on each eligible game frame, at most once every 750 ms. */
    fun onFrame(
        now: Long,
        hud: Boolean,
        controlsVisible: Boolean,
        panel: Boolean,
        targetHp: Int?,
        targetDecision: TargetAction
    ): CombatStep {
        timeCheck(now)
        if (!running) return CombatStep(null, status, mode)
        uiSince = 0L
        if (!hud) {
            hudMissing++
            controlsStable = 0
            if (hudMissing >= 4) pause("не распознан HUD на 4 кадрах")
            return CombatStep(null, status, mode)
        }
        hudMissing = 0
        controlsStable = if (controlsVisible) (controlsStable + 1).coerceAtMost(8) else 0
        if (!controlsVisible && now - phaseSince > 15_000L)
            pause("кнопки управления не распознаны более 15 секунд")
        if (!running || waitingCallback) return CombatStep(null, status, mode)

        if (panel && targetHp != null && targetHp > 0) {
            targetValid = (targetValid + 1).coerceAtMost(8)
            unknownHpSince = 0L
            targetMissing = 0
        } else if (panel) {
            targetValid = 0
            targetMissing = 0
            if (unknownHpSince == 0L) unknownHpSince = now
            if (now - unknownHpSince > 9_000L)
                pause("панель цели есть, но HP не читается 9 секунд")
        } else {
            targetValid = 0
            unknownHpSince = 0L
            targetMissing++
        }
        if (!running) return CombatStep(null,status,mode)

        when (mode) {
            CombatMode.SEARCH -> {
                if (panel) {
                    if (targetValid >= 2 && targetDecision == TargetAction.ATTACK) {
                        mode = CombatMode.READY_ATTACK
                        phaseSince = now
                        confirmedSelections++
                        status = "Выбрана живая цель; готовлю одиночную атаку"
                    }
                    return CombatStep(null,status,mode)
                }
                if (targetMissing >= 2 && controlsStable >= 2 &&
                    now - lastGestureAt >= settings.tapIntervalMs) {
                    return send(CombatTap.TARGET, now, "Нажимаю «Цель»")
                }
            }
            CombatMode.WAIT_TARGET -> {
                if (panel && targetValid >= 2 && targetDecision == TargetAction.ATTACK) {
                    mode = CombatMode.READY_ATTACK
                    phaseSince = now
                    confirmedSelections++
                    status = "Цель выбрана после нажатия; ожидаю возможность атаки"
                } else if (now - phaseSince >= settings.tapIntervalMs) {
                    mode = CombatMode.SEARCH
                    phaseSince = now
                    status = "Панель цели не подтверждена; продолжаю поиск"
                }
            }
            CombatMode.READY_ATTACK -> {
                if (!panel) {
                    mode = CombatMode.SEARCH
                    phaseSince = now
                    status = "Цель пропала перед атакой; ищу следующую"
                    return CombatStep(null,status,mode)
                }
                if (targetValid >= 2 && targetDecision == TargetAction.ATTACK &&
                    controlsStable >= 2 && now - lastGestureAt >= settings.tapIntervalMs &&
                    now - phaseSince >= 750L)
                    return send(CombatTap.ATTACK, now,
                        "Нажимаю «Атака» один раз; затем наблюдаю HP")
            }
            CombatMode.WATCH_ATTACK -> {
                if (!panel && targetMissing >= 2) {
                    targetsLost++
                    mode = CombatMode.SEARCH
                    phaseSince = now
                    beforeHp = null
                    status = "Панель цели исчезла; ищу следующую (гибель не доказана)"
                    return CombatStep(null,status,mode)
                }
                val baseline = beforeHp
                if (panel && targetHp != null && baseline != null) {
                    if (targetHp >= baseline + 30) {
                        pause("HP цели резко выросло: возможна смена противника")
                    } else {
                        lowerHpFrames = if (targetHp <= baseline - 2)
                            lowerHpFrames + 1 else 0
                        if (lowerHpFrames >= 2 && !damageObserved) {
                            damageObserved = true
                            damageObservations++
                            status = "После касания HP снизилось с $baseline% до $targetHp%; " +
                                "наблюдаю бой, повторных атак не отправляю"
                        }
                    }
                }
                if (running && !damageObserved && now - phaseSince >= 12_000L)
                    pause("за 12 секунд снижение HP после атаки не подтверждено")
                if (running && now - phaseSince >= 90_000L)
                    pause("цель не исчезла за 90 секунд")
            }
            else -> Unit
        }
        return CombatStep(null,status,mode)
    }

    private fun send(tap: CombatTap, now: Long, message: String): CombatStep {
        if (sentGestures >= settings.maxGestures) {
            pause("достигнут предел ${settings.maxGestures} касаний")
            return CombatStep(null,status,mode)
        }
        sentGestures++
        lastGestureAt = now
        gestureRequestedAt = now
        waitingCallback = true
        expectedTap = tap
        status = "$message (${sentGestures}/${settings.maxGestures})"
        return CombatStep(tap,status,mode)
    }

    fun statistics(now: Long): CombatStatistics = CombatStatistics(
        elapsedSeconds = (if (startedAt == 0L && lastObservedAt == 0L) 0L
            else ((finishedAt ?: now) - startedAt).coerceAtLeast(0L) / 1000L),
        targetTaps = successfulTargetTaps,
        attackTaps = successfulAttackTaps,
        confirmedSelections = confirmedSelections,
        damageObservations = damageObservations,
        lostPanels = targetsLost,
        attemptedGestures = sentGestures,
        gestureLimit = settings.maxGestures
    )

    fun configuredSettings(): CombatSettings = settings

    /** Called after the AccessibilityService gesture completion callback. */
    fun onGestureResult(now: Long, tap: CombatTap, ok: Boolean, observedHp: Int?) {
        if (running) lastObservedAt = now
        if (!running || !waitingCallback || tap != expectedTap) return
        waitingCallback = false
        expectedTap = null
        if (!ok) {
            pause("Android отменил касание $tap")
            return
        }
        phaseSince = now
        if (tap == CombatTap.TARGET) {
            successfulTargetTaps++
            mode = CombatMode.WAIT_TARGET
            status = "«Цель» нажата; ожидаю изменение панели противника"
        } else {
            successfulAttackTaps++
            mode = CombatMode.WATCH_ATTACK
            beforeHp = observedHp
            lowerHpFrames = 0
            damageObserved = false
            status = "«Атака» нажата; проверяю HP цели без повторного касания"
        }
    }
}
