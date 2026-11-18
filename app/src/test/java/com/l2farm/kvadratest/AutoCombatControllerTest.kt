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

    @Test fun repeatedTargetSelectionsAreBounded() {
        val c=AutoCombatController()
        c.start(0L)
        for (i in 0..2) {
            val base=i*12000L
            step(c,base+800L)
            val t=step(c,base+1600L)
            assertEquals(CombatTap.TARGET,t.tap)
            c.onGestureResult(base+1700L,CombatTap.TARGET,true,null)
            step(c,base+8500L)
        }
        val result=step(c,37000L)
        assertNull(result.tap)
        assertEquals(CombatMode.PAUSED,c.mode)
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
