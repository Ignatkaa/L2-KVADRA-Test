package com.l2farm.kvadratest

import org.junit.Assert.*
import org.junit.Test

class CombatSettingsTest {
    @Test fun validRangeIsOneToOneHundredTwentyMinutesAndTwoToTenSeconds() {
        assertEquals(CombatSettings(1,500L),CombatSettings.safe(-1,50L))
        assertEquals(CombatSettings(120,10000L),CombatSettings.safe(999,50_000L))
        assertEquals(CombatSettings(120,2000L),CombatSettings.safe(120,2000L))
        assertEquals(CombatSettings(120,500L),CombatSettings.safe(120,500L))
        assertEquals(CombatSettings(15,1000L),CombatSettings.safe(15,1000L))
        assertEquals(CombatSettings(1,5000L),CombatSettings.safe(1,5000L))
        assertEquals(CombatSettings(3,3500L),CombatSettings.safe(3,3500L))
        assertEquals(CombatSettings(4,2500L),CombatSettings.safe(4,2850L))
    }

    @Test fun perSessionMaxGesturesScalesToAllowFullLongRun() {
        assertEquals(31,CombatSettings.safe(1,2000L).maxGestures)
        assertEquals(52,CombatSettings().maxGestures)
        assertEquals(3601,CombatSettings.safe(120,2000L).maxGestures)
        assertEquals(14401,CombatSettings.safe(120,500L).maxGestures)
        assertEquals(7201,CombatSettings.safe(120,1000L).maxGestures)
        assertEquals(721,CombatSettings.safe(120,10000L).maxGestures)
    }

    @Test fun maximumTwoHourSessionOnlyStopsAtConfiguredEnd() {
        val controller = AutoCombatController()
        controller.start(1000L,CombatSettings.safe(120,2000L))
        controller.timeCheck(601000L)
        assertTrue("ten minutes must not stop two-hour session",controller.running)
        controller.timeCheck(7200999L)
        assertTrue(controller.running)
        controller.timeCheck(7201000L)
        assertEquals(CombatMode.PAUSED,controller.mode)
        assertEquals(7200L,controller.statistics(100000000L).elapsedSeconds)
        assertEquals(3601,controller.statistics(7201000L).gestureLimit)
    }

    @Test fun halfSecondTargetSearchNeverViolatesConfiguredMinimum() {
        val controller=AutoCombatController()
        controller.start(1000L,CombatSettings.safe(5,500L))
        controller.onFrame(1250L,true,true,false,null,TargetAction.SEARCH)
        val first=controller.onFrame(1500L,true,true,false,null,TargetAction.SEARCH)
        assertEquals(CombatTap.TARGET,first.tap)
        controller.onGestureResult(1600L,CombatTap.TARGET,true,null)
        // WAIT_TARGET observes a missed target; it does not immediately tap.
        assertNull(controller.onFrame(
            1850L,true,true,false,null,TargetAction.SEARCH).tap)
        assertNull(controller.onFrame(
            2100L,true,true,false,null,TargetAction.SEARCH).tap)
        assertEquals(CombatTap.TARGET,controller.onFrame(
            2350L,true,true,false,null,TargetAction.SEARCH).tap)
    }

    @Test fun oneSecondSettingIsAcceptedAndRateLimited() {
        val settings=CombatSettings.safe(5,1000L)
        assertEquals(1000L,settings.tapIntervalMs)
        val controller=AutoCombatController()
        controller.start(1000L,settings)
        controller.onFrame(1250L,true,true,false,null,TargetAction.SEARCH)
        val first=controller.onFrame(1600L,true,true,false,null,TargetAction.SEARCH)
        assertEquals(CombatTap.TARGET,first.tap)
        controller.onGestureResult(1750L,CombatTap.TARGET,true,null)
        assertNull(controller.onFrame(
            2200L,true,true,false,null,TargetAction.SEARCH).tap)
        assertNull(controller.onFrame(
            2800L,true,true,false,null,TargetAction.SEARCH).tap)
        assertEquals(CombatTap.TARGET,controller.onFrame(
            3150L,true,true,false,null,TargetAction.SEARCH).tap)
    }

    @Test fun customIntervalPreventsPrematureSecondTap() {
        val controller=AutoCombatController()
        controller.start(1000L,CombatSettings.safe(5,10000L))
        controller.onFrame(1900L,true,true,false,null,TargetAction.SEARCH)
        val first=controller.onFrame(2800L,true,true,false,null,TargetAction.SEARCH)
        assertEquals(CombatTap.TARGET,first.tap)
        controller.onGestureResult(2900L,CombatTap.TARGET,true,null)
        controller.onFrame(14000L,true,true,false,null,TargetAction.SEARCH)
        // WAIT_TARGET expires after configured wait, but interval remains 10s.
        val next=controller.onFrame(15000L,true,true,false,null,TargetAction.SEARCH)
        assertEquals(CombatTap.TARGET,next.tap)
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
