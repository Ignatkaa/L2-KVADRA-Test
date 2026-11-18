package com.l2farm.kvadratest

import org.junit.Assert.*
import org.junit.Test

class AutoCombatControllerTest {
    private fun step(
        c: AutoCombatController, t: Long, panel: Boolean = false,
        hp: Int? = null, hud: Boolean = true, controls: Boolean = true,
        action: TargetAction = if (panel && hp != null) TargetAction.ATTACK
            else TargetAction.SEARCH
    ) = c.onFrame(t,hud,controls,panel,hp,action)

    @Test fun fullCycleHasOnlyOneAttackPerTargetAndSelectsNext() {
        val c=AutoCombatController()
        c.start(1000L)
        assertNull(step(c,1750L).tap)
        val target=step(c,2500L)
        assertEquals(CombatTap.TARGET,target.tap)
        c.onGestureResult(2650L,CombatTap.TARGET,true,null)
        assertNull(step(c,3600L,true,99,action=TargetAction.ACQUIRING).tap)
        assertNull(step(c,4400L,true,99).tap)
        assertNull(step(c,5300L,true,98).tap)
        val attack=step(c,6200L,true,98)
        assertEquals(CombatTap.ATTACK,attack.tap)
        c.onGestureResult(6350L,CombatTap.ATTACK,true,98)
        assertNull(step(c,7200L,true,97).tap)
        assertNull(step(c,8000L,true,90).tap)
        assertNull(step(c,8800L,true,89).tap)
        assertEquals(CombatMode.WATCH_ATTACK,c.mode)
        // Stay in WATCH_ATTACK. Never spam Attack.
        assertNull(step(c,10300L,true,50).tap)
        assertNull(step(c,11400L,false).tap)
        assertNull(step(c,12300L,false).tap)
        assertEquals(CombatMode.SEARCH,c.mode)
        val next=step(c,13400L,false)
        assertEquals(CombatTap.TARGET,next.tap)
        assertEquals(3,c.sentGestures)
    }

    @Test fun unknownHpBlocksBlindAttackAndPauses() {
        val c=AutoCombatController()
        c.start(1000L)
        step(c,2000L,true,null)
        val outcome=step(c,11200L,true,null)
        assertEquals(CombatMode.PAUSED,outcome.mode)
        assertEquals(0,c.sentGestures)
    }

    @Test fun missingButtonsNeverProducesTap() {
        val c=AutoCombatController()
        c.start(1000L)
        step(c,2000L,false,null,controls=false)
        val outcome=step(c,17000L,false,null,controls=false)
        assertNull(outcome.tap)
        assertEquals(CombatMode.PAUSED,c.mode)
    }

    @Test fun gestureCancelledPausesWithoutRetry() {
        val c=AutoCombatController()
        c.start(0L)
        step(c,1000L)
        val target=step(c,2000L)
        assertEquals(CombatTap.TARGET,target.tap)
        c.onGestureResult(2100L,CombatTap.TARGET,false,null)
        assertEquals(CombatMode.PAUSED,c.mode)
        assertNull(step(c,2200L).tap)
    }

    @Test fun timeoutAndStopPreventFurtherGestures() {
        val c=AutoCombatController()
        c.start(0L)
        c.timeCheck(180000L)
        assertEquals(CombatMode.PAUSED,c.mode)
        c.start(200000L)
        c.stop()
        assertNull(step(c,202000L).tap)
        assertEquals(CombatMode.OFF,c.mode)
    }

    @Test fun moreThanThreeFailedTargetAttemptsKeepSearching() {
        val c = AutoCombatController()
        c.start(1000L,CombatSettings.safe(5,2000L))
        // Simulate twelve failed searches without a selected target panel.
        for (i in 0 until 12) {
            val base = 1000L + i*4000L
            step(c,base+800L)
            val result=step(c,base+1600L)
            assertEquals("attempt $i",CombatTap.TARGET,result.tap)
            c.onGestureResult(base+1700L,CombatTap.TARGET,true,null)
            assertTrue("search $i should not pause",c.running)
        }
        assertEquals(12,c.sentGestures)
        assertEquals(12,c.statistics(51000L).targetTaps)
        assertNotEquals(CombatMode.PAUSED,c.mode)
    }

    @Test fun moreThanSixLostPanelsDoNotStopCombat() {
        val c = AutoCombatController()
        c.start(1000L,CombatSettings.safe(5,2000L))
        for (i in 0 until 8) {
            val base=1000L+i*20000L
            step(c,base+750L,true,99,action=TargetAction.ACQUIRING)
            step(c,base+1500L,true,99)
            val hit=step(c,base+2400L,true,99)
            assertEquals(CombatTap.ATTACK,hit.tap)
            c.onGestureResult(base+2600L,CombatTap.ATTACK,true,99)
            step(c,base+3500L,true,92)
            step(c,base+4300L,true,90)
            step(c,base+5200L,false)
            val lost=step(c,base+6000L,false)
            assertEquals("lost panel $i",CombatMode.SEARCH,lost.mode)
            assertTrue(c.running)
        }
        assertEquals(8,c.targetsLost)
        assertEquals(8,c.statistics(175000L).attackTaps)
    }

    @Test fun ownUiPausesAfterTwelveSeconds() {
        val c=AutoCombatController()
        c.start(0L)
        c.onOwnUi(1000L)
        c.onOwnUi(14000L)
        assertEquals(CombatMode.PAUSED,c.mode)
    }

    @Test fun targetSuddenHpRisePausesAsAmbiguous() {
        val c=AutoCombatController()
        c.start(0L)
        step(c,800L,true,70,action=TargetAction.ACQUIRING)
        step(c,1600L,true,70)
        val attack=step(c,2400L,true,70)
        assertEquals(CombatTap.ATTACK,attack.tap)
        c.onGestureResult(2500L,CombatTap.ATTACK,true,70)
        step(c,3500L,true,100)
        assertEquals(CombatMode.PAUSED,c.mode)
    }
}
