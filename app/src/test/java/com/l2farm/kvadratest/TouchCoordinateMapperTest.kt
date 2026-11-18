package com.l2farm.kvadratest

import org.junit.Assert.*
import org.junit.Test

class TouchCoordinateMapperTest {
    @Test fun mapsKvadrCaptureToActualScreen() {
        // Source dimensions measured from the user's actual attached PNGs:
        // MediaProjection PNG: 2000x1110; screenshot: 2000x1200.
        val target = TouchCoordinateMapper.map(
            GameControlPoint(1705,1058),2000,1110,2000,1200
        )!!
        assertEquals(1762,target.point.x)
        assertEquals(1144,target.point.y)
        assertEquals(0.925,target.scale,0.00001)
        assertEquals(75.0,target.insetX,0.01)
        assertEquals(0.0,target.insetY,0.01)
    }

    @Test fun mapsRadialAttackToRealDisplay() {
        val attack=TouchCoordinateMapper.map(
            GameControlPoint(1815,947),2000,1110,2000,1200
        )!!
        assertEquals(1881,attack.point.x)
        assertEquals(1024,attack.point.y)
    }

    @Test fun classicTargetAlsoNeedsRemapping() {
        val classic=TouchCoordinateMapper.map(
            GameControlPoint(1687,754),2000,1110,2000,1200
        )!!
        assertEquals(1743,classic.point.x)
        assertEquals(815,classic.point.y)
    }

    @Test fun sourceEqualToDisplayUsesIdentityMapping() {
        val p=TouchCoordinateMapper.map(
            GameControlPoint(1750,1000),2000,1200,2000,1200
        )!!
        assertEquals(1750,p.point.x)
        assertEquals(1000,p.point.y)
        assertEquals(1.0,p.scale,0.0001)
    }

    @Test fun refusesBlackBarsAndOutOfScreenPoints() {
        assertNull(TouchCoordinateMapper.map(GameControlPoint(30,100),
            2000,1110,2000,1200))
        assertNull(TouchCoordinateMapper.map(GameControlPoint(1950,100),
            2000,1110,2000,1200))
        assertNull(TouchCoordinateMapper.map(GameControlPoint(1700,1109),
            2000,1110,2000,1200))
    }
}
