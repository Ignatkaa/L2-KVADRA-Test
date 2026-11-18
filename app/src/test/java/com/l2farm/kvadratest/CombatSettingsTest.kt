package com.l2farm.kvadratest

import org.junit.Assert.*
import org.junit.Test

class CombatSettingsTest {
    @Test fun safeSettingsAcceptOnlyWhitelistedDurationsAndIntervals() {
        assertEquals(CombatSettings(3,3500L),CombatSettings.safe(-1,50L))
        assertEquals(CombatSettings(3,3500L),CombatSettings.safe(999,500L))
        assertEquals(CombatSettings(10,10000L),CombatSettings.safe(10,10000L))
        assertEquals(CombatSettings(1,5000L),CombatSettings.safe(1,5000L))
    }

    @Test fun perSessionTapLimitsRemainFinite() {
        assertEquals(4,CombatSettings.safe(1,3500L).maxGestures)
        assertEquals(12,CombatSettings().maxGestures)
        assertEquals(20,CombatSettings.safe(5,7000L).maxGestures)
        assertEquals(40,CombatSettings.safe(10,10000L).maxGestures)
    }

    @Test fun customDurationStopsAndFreezesElapsedTime() {
        val controller = AutoCombatController()
        controller.start(1000L,CombatSettings.safe(1,7000L))
        controller.timeCheck(60500L)
        assertTrue(controller.running)
        controller.timeCheck(61000L)
        assertEquals(CombatMode.PAUSED,controller.mode)
        assertEquals(60L,controller.statistics(1_000_000L).elapsedSeconds)
        assertEquals(4,controller.statistics(61000L).gestureLimit)
    }

    @Test fun customIntervalPreventsPrematureSecondTap() {
        val controller=AutoCombatController()
        controller.start(1000L,CombatSettings.safe(5,10000L))
        controller.onFrame(1900L,true,true,false,null,TargetAction.SEARCH)
        val first=controller.onFrame(2800L,true,true,false,null,TargetAction.SEARCH)
        assertEquals(CombatTap.TARGET,first.tap)
        controller.onGestureResult(2900L,CombatTap.TARGET,true,null)
        controller.onFrame(10100L,true,true,false,null,TargetAction.SEARCH)
        // WAIT_TARGET has expired and SEARCH entered, but less than 10s from first tap
        val early=controller.onFrame(11100L,true,true,false,null,TargetAction.SEARCH)
        assertNull(early.tap)
        val late=controller.onFrame(13000L,true,true,false,null,TargetAction.SEARCH)
        assertEquals(CombatTap.TARGET,late.tap)
    }

    @Test fun statisticsTrackSuccessfulGesturesNotJustAttemptedOnes() {
        val controller=AutoCombatController()
        controller.start(1000L)
        controller.onFrame(1750L,true,true,false,null,TargetAction.SEARCH)
        val first=controller.onFrame(2500L,true,true,false,null,TargetAction.SEARCH)
        assertEquals(CombatTap.TARGET,first.tap)
        assertEquals(1,controller.statistics(2500L).attemptedGestures)
        assertEquals(0,controller.statistics(2500L).targetTaps)
        controller.onGestureResult(2700L,CombatTap.TARGET,true,null)
        assertEquals(1,controller.statistics(2700L).targetTaps)
        assertEquals(0,controller.statistics(2700L).attackTaps)
    }
}
