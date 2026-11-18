package com.l2farm.kvadratest

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class ScreenTapPoint(val x: Int, val y: Int)

data class TouchMappingResult(
    val point: ScreenTapPoint,
    val scale: Double,
    val insetX: Double,
    val insetY: Double,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val screenWidth: Int,
    val screenHeight: Int
) {
    fun describe(): String =
        "capture=${sourceWidth}x${sourceHeight}, screen=${screenWidth}x${screenHeight}, " +
            "scale=${"%.4f".format(java.util.Locale.US,scale)}, " +
            "letterbox=(${insetX.roundToInt()},${insetY.roundToInt()})"
}

/**
 * Converts a MediaProjection ImageReader pixel to a physical display touch.
 *
 * MediaProjection scales the real display uniformly to fit the requested virtual
 * display surface and centers the result (Android 12L+). Coordinates detected
 * in the image MUST be mapped back to physical display coordinates before a
 * GestureDescription can be dispatched.
 *
 * Example on KVADRA_T:
 *   screen 2000x1200 -> projection 2000x1110
 *   scale 0.925, horizontal bars 75 px per side
 *   image target (1705,1058) -> display target (1762,1144)
 *
 * This mapper never extrapolates from black-bar pixels.
 */
object TouchCoordinateMapper {
    fun map(
        point: GameControlPoint,
        sourceWidth: Int,
        sourceHeight: Int,
        screenWidth: Int,
        screenHeight: Int
    ): TouchMappingResult? {
        if (sourceWidth < 400 || sourceHeight < 300 ||
            screenWidth < 400 || screenHeight < 300) return null
        if (sourceWidth <= sourceHeight || screenWidth <= screenHeight) return null
        val scale = min(
            sourceWidth.toDouble() / screenWidth,
            sourceHeight.toDouble() / screenHeight
        )
        if (!scale.isFinite() || scale <= 0.0 || scale > 2.0) return null
        val insetX = (sourceWidth - screenWidth * scale) / 2.0
        val insetY = (sourceHeight - screenHeight * scale) / 2.0
        if (insetX < -1.0 || insetY < -1.0) return null
        val realX = (point.x - insetX) / scale
        val realY = (point.y - insetY) / scale
        if (realX < 0.0 || realY < 0.0 ||
            realX >= screenWidth || realY >= screenHeight) return null
        val ix = realX.roundToInt().coerceIn(0, screenWidth - 1)
        val iy = realY.roundToInt().coerceIn(0, screenHeight - 1)
        // Very bottom / far edge is not a safe gesture landing zone.
        if (ix < 8 || iy < 8 || ix >= screenWidth - 8 ||
            iy >= screenHeight - 8) return null
        return TouchMappingResult(
            ScreenTapPoint(ix,iy), scale, insetX, insetY,
            sourceWidth, sourceHeight, screenWidth, screenHeight
        )
    }
}
