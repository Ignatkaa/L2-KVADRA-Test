package com.l2farm.kvadratest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetCombatTrackerTest {
    @Test fun attackRequiresTwoConsecutiveConfirmedFrames() {
        val tracker = TargetCombatTracker()
        assertEquals(TargetAction.ACQUIRING, tracker.update(true, true, 78).action)
        val second = tracker.update(true, true, 65)
        assertEquals(TargetAction.ATTACK, second.action)
        assertEquals(2, second.consecutiveValidFrames)
    }

    @Test fun absentTargetSuggestsSearchingOnlyAfterDebounce() {
        val tracker = TargetCombatTracker()
        assertEquals(TargetAction.VERIFY_TARGET, tracker.update(true, false, null).action)
        assertEquals(TargetAction.SEARCH, tracker.update(true, false, null).action)
        assertEquals(TargetAction.ACQUIRING, tracker.update(true, true, 92).action)
        assertEquals(TargetAction.ATTACK, tracker.update(true, true, 92).action)
    }

    @Test fun emptyOrUnreadableTargetHpNeverBecomesAnAttack() {
        val tracker = TargetCombatTracker()
        assertEquals(TargetAction.VERIFY_TARGET, tracker.update(true, true, null).action)
        assertEquals(TargetAction.VERIFY_TARGET, tracker.update(true, true, 0).action)
        assertEquals(TargetAction.ACQUIRING, tracker.update(true, true, 14).action)
        assertEquals(TargetAction.ATTACK, tracker.update(true, true, 11).action)
        assertEquals(TargetAction.VERIFY_TARGET, tracker.update(true, true, null).action)
    }

    @Test fun lostTargetDoesNotClaimConfirmedDeath() {
        val tracker = TargetCombatTracker()
        tracker.update(true, true, 8)
        tracker.update(true, true, 5)
        assertEquals(TargetAction.VERIFY_TARGET, tracker.update(true, false, null).action)
        val lost = tracker.update(true, false, null)
        assertEquals(TargetAction.TARGET_LOST, lost.action)
        assertTrue(lost.recommendation.contains("не подтверждена"))
        assertEquals(TargetAction.SEARCH, tracker.update(true, false, null).action)
    }

    @Test fun suddenHpRiseRequiresTargetReconfirmation() {
        val tracker = TargetCombatTracker()
        tracker.update(true, true, 35)
        assertEquals(TargetAction.ATTACK, tracker.update(true, true, 20).action)
        assertEquals(TargetAction.ACQUIRING, tracker.update(true, true, 95).action)
        assertEquals(TargetAction.ATTACK, tracker.update(true, true, 94).action)
    }

    @Test fun hudLossDoesNotAdvanceNoTargetCounter() {
        val tracker = TargetCombatTracker()
        assertEquals(TargetAction.HUD_UNAVAILABLE, tracker.update(false, false, null).action)
        assertEquals(TargetAction.VERIFY_TARGET, tracker.update(true, false, null).action)
        assertEquals(TargetAction.SEARCH, tracker.update(true, false, null).action)
    }

    @Test fun characterHpOrMpCannotGateTargetAttack() {
        val observation = FarmObservation(
            hp = 1, mp = 0, targetVisible = true, targetHp = 45
        )
        assertTrue(ArcherDecisionEngine.advise(observation).contains("атака"))
    }
}
