package com.l2farm.kvadratest

import org.junit.Assert.*
import org.junit.Test

class AttackImpactMonitorTest {
    @Test fun needsTwoMeasurementsOfLowerHp() {
        val monitor = AttackImpactMonitor()
        monitor.start(85,0L)
        assertTrue(monitor.isActive())
        assertNull(monitor.observe(1500L,true,85))
        assertNull(monitor.observe(2500L,true,65))
        val outcome=monitor.observe(3500L,true,64)
        assertNotNull(outcome)
        assertTrue(outcome!!.contains("85% → 64%"))
        assertTrue(outcome.contains("причинная связь не доказана"))
        assertFalse(monitor.isActive())
    }

    @Test fun absentHpIsNotAConfirmedAttack() {
        val monitor=AttackImpactMonitor()
        monitor.start(99,0L)
        assertNull(monitor.observe(1000L,true,null))
        assertNull(monitor.observe(2000L,true,null))
        assertNull(monitor.observe(5000L,true,99))
        val outcome=monitor.observe(21000L,true,null)
        assertTrue(outcome!!.contains("не подтверждено"))
        assertFalse(monitor.isActive())
    }

    @Test fun lostTargetIsNotReportedAsDamage() {
        val monitor=AttackImpactMonitor()
        monitor.start(99,0L)
        assertNull(monitor.observe(1000L,false,null))
        val outcome=monitor.observe(2000L,false,null)
        assertTrue(outcome!!.contains("панель цели исчезла"))
    }

    @Test fun noValidBeforeHpDoesNotArmMonitor() {
        val monitor=AttackImpactMonitor()
        monitor.start(null,0L)
        assertFalse(monitor.isActive())
        assertNull(monitor.observe(1000L,true,55))
        monitor.start(0,2000L)
        assertFalse(monitor.isActive())
    }

    @Test fun isolatedLowerReadingDoesNotConfirm() {
        val monitor=AttackImpactMonitor()
        monitor.start(85,0L)
        assertNull(monitor.observe(1000L,true,60))
        assertNull(monitor.observe(2000L,true,85))
        assertNull(monitor.observe(3000L,true,60))
        assertNotNull(monitor.observe(4000L,true,59))
    }
}
