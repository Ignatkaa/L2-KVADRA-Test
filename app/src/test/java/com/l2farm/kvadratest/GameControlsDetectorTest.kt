package com.l2farm.kvadratest

import org.junit.Assert.*
import org.junit.Test

class GameControlsDetectorTest {
    private val outline = (106 shl 16) or (166 shl 8) or 217
    private val red = (122 shl 16) or (42 shl 8) or 32
    private val black = (20 shl 16) or (20 shl 8) or 24

    private fun frame(layout: GameControlsLayout, withAttack: Boolean = true):
        (Int, Int) -> Int? {
        val classic = layout == GameControlsLayout.CLASSIC
        val top = if (classic) 716 else 1024
        val left = if (classic) 1640 else 1666
        val right = if (classic) 1734 else 1751
        val attackX = if (classic) 1859 else 1815
        val attackY = if (classic) 861 else 947
        val radius = if (classic) 56 else 69
        return { x, y ->
            val border = (y == top && x in left..right) ||
                (y in top..(top + 76) && (x == left || x == right))
            val dx = x - attackX
            val dy = y - attackY
            val circle = withAttack && dx * dx + dy * dy <= radius * radius
            when {
                border -> outline
                circle -> red
                else -> black
            }
        }
    }

    @Test fun detectsClassicControls() {
        val found = GameControlsDetector.detect(2000, 1110, frame(GameControlsLayout.CLASSIC))
        assertNotNull(found)
        assertEquals(GameControlsLayout.CLASSIC, found!!.layout)
        assertTrue(kotlin.math.abs(found.target.x - 1687) <= 2)
        assertTrue(kotlin.math.abs(found.target.y - 754) <= 2)
        assertTrue(kotlin.math.abs(found.attack.x - 1859) <= 2)
        assertTrue(kotlin.math.abs(found.attack.y - 861) <= 2)
    }

    @Test fun detectsRadialControls() {
        val found = GameControlsDetector.detect(2000, 1110, frame(GameControlsLayout.RADIAL))
        assertNotNull(found)
        assertEquals(GameControlsLayout.RADIAL, found!!.layout)
        assertTrue(kotlin.math.abs(found.target.x - 1709) <= 2)
        assertTrue(kotlin.math.abs(found.target.y - 1062) <= 2)
        assertTrue(kotlin.math.abs(found.attack.x - 1815) <= 2)
        assertTrue(kotlin.math.abs(found.attack.y - 947) <= 2)
    }

    @Test fun refusesMissingAttackButtonOrBlueOutline() {
        assertNull(GameControlsDetector.detect(2000, 1110,
            frame(GameControlsLayout.CLASSIC, withAttack = false)))
        assertNull(GameControlsDetector.detect(2000, 1110) { _,_ -> black })
    }

    @Test fun refusesPortraitAndIncorrectAspectRatio() {
        assertNull(GameControlsDetector.detect(555, 1000) { _,_ -> outline })
        assertNull(GameControlsDetector.detect(1800, 1000) { _,_ -> black })
    }
}
