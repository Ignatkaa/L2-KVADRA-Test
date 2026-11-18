package com.l2farm.kvadratest

import org.junit.Assert.*
import org.junit.Test

class ManualTapPolicyTest {
    private val buttons = GameControls(
        GameControlsLayout.RADIAL, GameControlPoint(1708,1062),
        GameControlPoint(1815,947),86,1481
    )

    private fun decision(action: TargetAction, hp: Int? = null) =
        TargetDecision(action, hp, 1, "test")

    @Test fun targetTestWorksEvenWhenEnemyAlreadySelected() {
        for (state in listOf(TargetAction.SEARCH, TargetAction.ATTACK,
            TargetAction.VERIFY_TARGET, TargetAction.ACQUIRING)) {
            assertTrue(state.name,
                ManualTapPolicy.check("TARGET",true,buttons,decision(state,95)).allowed)
        }
    }
    @Test fun attackRequiresConfirmedAliveEnemy() {
        assertTrue(ManualTapPolicy.check("ATTACK",true,buttons,
            decision(TargetAction.ATTACK,45)).allowed)
        assertFalse(ManualTapPolicy.check("ATTACK",true,buttons,
            decision(TargetAction.ACQUIRING,95)).allowed)
        assertFalse(ManualTapPolicy.check("ATTACK",true,buttons,
            decision(TargetAction.ATTACK,null)).allowed)
        assertFalse(ManualTapPolicy.check("ATTACK",true,buttons,
            decision(TargetAction.ATTACK,0)).allowed)
    }
    @Test fun blocksMissingHudOrControlsWithHelpfulReason() {
        val d=decision(TargetAction.ATTACK,70)
        val noHud=ManualTapPolicy.check("TARGET",false,buttons,d)
        assertFalse(noHud.allowed)
        assertTrue(noHud.reason.contains("HUD"))
        val noButtons=ManualTapPolicy.check("TARGET",true,null,d)
        assertFalse(noButtons.allowed)
        assertTrue(noButtons.reason.contains("не распознаны"))
    }
}
