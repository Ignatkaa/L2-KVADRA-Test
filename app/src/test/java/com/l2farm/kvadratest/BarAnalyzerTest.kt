package com.l2farm.kvadratest

import org.junit.Assert.*
import org.junit.Test

class BarAnalyzerTest {
    /**
     * Solid-colour rows extracted from L2Meta_capture_1794951206409.png,
     * 2000x1110. A compact regression fixture, not the complete screenshot.
     * Ground truth on that PNG: CP 100, HP 3027/3063=98.82, MP 100, target 78.
     */
    @Test fun referenceFrameTargetIs78Percent() {
        val bar = BarAnalyzer.scan(755,1078,72) { x,y ->
            y in 71..73 && x in 756..1007
        }
        assertNotNull(bar.percent)
        assertTrue("target ${bar.percent}", kotlin.math.abs(bar.percent!! - 78) <= 3)
        assertTrue("correct Y", bar.scanY in 71..73)
    }

    @Test fun referenceFramePlayerBarsAreNearFull() {
        val cp = BarAnalyzer.scan(151,383,92) { x,y ->
            y in 94..96 && x in 154..383
        }
        val hp = BarAnalyzer.scan(151,383,117) { x,y ->
            y in 121..123 && x in 154..383
        }
        val mp = BarAnalyzer.scan(151,383,142) { x,y ->
            y in 148..150 && x in 154..383
        }
        assertEquals(100, cp.percent)
        assertTrue(kotlin.math.abs(hp.percent!! - (3027.0 / 3063 * 100)) <= 3.0)
        assertEquals(100, mp.percent)
    }

    @Test fun partialBarsAndMissingTargetsAreDistinct() {
        val cp = BarAnalyzer.scan(151,383,92) { x,y ->
            y in 94..96 && x in 154..255
        }
        assertTrue("partial CP", kotlin.math.abs(cp.percent!! - 44) <= 3)
        val noTarget = BarAnalyzer.scan(755,1078,72) { _,_ -> false }
        assertNull(noTarget.percent)
        val unrelatedRed = BarAnalyzer.scan(755,1078,72) { x,y ->
            y in 71..73 && x in 1040..1060
        }
        assertNull("red decoration to the right is not HP", unrelatedRed.percent)
    }
}
