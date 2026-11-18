package com.l2farm.kvadratest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetTransitionHistoryTest {
    private fun decision(action: TargetAction, hp: Int? = null) =
        TargetDecision(action, hp, 1, "test")

    @Test fun recordsChangesWithoutDuplicatePerFrameSpam() {
        val history = TargetTransitionHistory()
        assertTrue(history.record(1, 800, decision(TargetAction.ACQUIRING, 100), true))
        assertFalse(history.record(2, 1600, decision(TargetAction.ACQUIRING, 98), true))
        assertTrue(history.record(3, 2400, decision(TargetAction.ATTACK, 95), true))
        assertFalse(history.record(4, 3200, decision(TargetAction.ATTACK, 60), true))
        assertTrue(history.record(5, 4000, decision(TargetAction.VERIFY_TARGET), false))
        assertTrue(history.record(6, 4800, decision(TargetAction.TARGET_LOST), false))
        assertTrue(history.record(7, 5600, decision(TargetAction.SEARCH), false))
        assertEquals(5, history.count())
        assertTrue(history.asText().contains("ATTACK → VERIFY_TARGET"))
        assertTrue(history.asText().contains("VERIFY_TARGET → TARGET_LOST"))
        assertTrue(history.asText().contains("TARGET_LOST → SEARCH"))
        assertTrue(history.asText().contains("кадр #6"))
    }

    @Test fun historyIsBoundedAndResetsBetweenSessions() {
        val history = TargetTransitionHistory(2)
        history.record(1, 0, decision(TargetAction.ATTACK, 100), true)
        history.record(2, 1000, decision(TargetAction.VERIFY_TARGET), false)
        history.record(3, 2000, decision(TargetAction.TARGET_LOST), false)
        assertEquals(2, history.count())
        assertFalse(history.asText().contains("кадр #1"))
        history.reset()
        assertEquals(0, history.count())
        assertEquals("Переходов пока нет", history.asText())
        assertTrue(history.record(1, 0, decision(TargetAction.SEARCH), false))
    }
}
