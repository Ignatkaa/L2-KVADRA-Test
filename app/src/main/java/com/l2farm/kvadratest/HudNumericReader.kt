package com.l2farm.kvadratest

import kotlin.math.abs
import kotlin.math.roundToInt

enum class HudStatRow(val top: Int) { CP(78), HP(105), MP(132) }

data class HudNumericValue(
    val current: Int,
    val maximum: Int,
    val percent: Int,
    val matchError: Int
)

/**
 * Lightweight OCR of L2Meta's fixed HUD digits at the calibrated 2000x1110 layout.
 * Templates are binary glyph shapes from player-bar text in reference game captures.
 * The '8' template is from a target-HP label and has less validation coverage.
 * No ML / Play services / network are required. Unknown or ambiguous text -> null.
 */
object HudNumericReader {
    private const val WIDTH = 116
    private const val HEIGHT = 16
    private const val START_X = 250
    private val glyphs = mapOf(
        '0' to intArrayOf(0x1f8,0x70e,0x70e,0x606,0xe07,0xe07,0xe07,0xe07,0xe07,0xe07,0xe07,0x607,0x606,0x70e,0x70e,0x1f8),
        '1' to intArrayOf(0x07f,0x3ff,0xf9f,0xf9f,0x01f,0x01f,0x01f,0x01f,0x01f,0x01f,0x01f,0x01f,0x01f,0x01f,0x01f,0x01f),
        '2' to intArrayOf(0x1e0,0x7fc,0x004,0x004,0x007,0x007,0x004,0x01c,0x01c,0x038,0x060,0x1c0,0x780,0x780,0xf80,0xfff),
        '3' to intArrayOf(0x040,0xffc,0x007,0x007,0x007,0x004,0x1f8,0x1f8,0x1f8,0x007,0x007,0x007,0x007,0x007,0x01f,0xffc),
        '4' to intArrayOf(0x018,0x018,0x078,0x078,0x0f8,0x088,0x108,0x608,0x608,0x608,0xe1e,0xfff,0x01e,0x01e,0x008,0x008),
        '5' to intArrayOf(0x7f8,0xffc,0xe00,0xe00,0xe00,0xe00,0xfe0,0xffc,0xffc,0x01c,0x007,0x007,0x007,0x007,0x01c,0xff8),
        '6' to intArrayOf(0x0fe,0x180,0x700,0x700,0x600,0xe00,0xff8,0xf0e,0xf0e,0xe06,0xe07,0xe06,0x60e,0x60e,0x7fe,0x0f0),
        '7' to intArrayOf(0x7ff,0xfff,0x007,0x007,0x007,0x01c,0x01c,0x018,0x018,0x038,0x038,0x060,0x060,0x060,0x1c0,0x1c0),
        '8' to intArrayOf(0x078,0x7fc,0x607,0x607,0x607,0x607,0x1dc,0x1f8,0x1f8,0x79c,0xe07,0xe03,0xe03,0xe03,0xe07,0x7fc),
        '9' to intArrayOf(0x1e0,0x7fc,0xe07,0xe07,0xe07,0x803,0xe07,0xf9f,0xf9f,0x7ff,0x007,0x007,0x004,0x004,0x03c,0x7e0)
    )

    /**
     * [rgb] returns the RGB components of a pixel in reference 2000x1110 coordinates.
     * Returns null if a character is missing, ambiguous or current/max is implausible.
     */
    fun read(row: HudStatRow, rgb: (Int, Int) -> IntArray?): HudNumericValue? {
        val white = BooleanArray(WIDTH * HEIGHT)
        for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
            val c = rgb(START_X + x, row.top + y)
            white[y * WIDTH + x] = c != null &&
                c[0] > 165 && c[1] > 165 && c[2] > 165 &&
                maxOf(c[0], c[1], c[2]) - minOf(c[0], c[1], c[2]) < 40
        }
        fun lit(x: Int, y: Int): Boolean {
            val xx = x - START_X
            return xx in 0 until WIDTH && y in 0 until HEIGHT && white[y * WIDTH + xx]
        }
        val left = (START_X until START_X + WIDTH).firstOrNull { x ->
            (0 until HEIGHT).any { lit(x, it) }
        } ?: return null
        val right = (START_X until START_X + WIDTH).lastOrNull { x ->
            (0 until HEIGHT).any { lit(x, it) }
        } ?: return null
        val shift = ((left - 260).toDouble() / 5.0).roundToInt().coerceIn(0, 3)
        val numeratorLength = 4 - shift
        if (abs(left - (260 + shift * 5)) > 3 ||
            abs(right - (357 - shift * 5)) > 5) return null

        fun digit(start: Int): Pair<Char, Int>? {
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = -1
            var maxY = -1
            for (y in 0 until HEIGHT) for (x in start until start + 11) {
                if (lit(x, y)) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
            if (maxX < minX || maxY < minY) return null
            val gw = maxX - minX + 1
            val gh = maxY - minY + 1
            val rows = IntArray(16)
            for (y in 0 until 16) {
                val sourceY = minY + ((2 * y + 1) * gh / 32).coerceIn(0, gh - 1)
                var bits = 0
                for (x in 0 until 12) {
                    val sourceX = minX + ((2 * x + 1) * gw / 24).coerceIn(0, gw - 1)
                    if (lit(sourceX, sourceY)) bits = bits or (1 shl (11 - x))
                }
                rows[y] = bits
            }
            val scores = glyphs.map { (c, tmpl) ->
                c to (0 until 16).sumOf { i -> Integer.bitCount(rows[i] xor tmpl[i]) }
            }.sortedBy { it.second }
            val best = scores[0]
            if (best.second > 62 || scores[1].second - best.second < 3) return null
            return best
        }

        val chars = ArrayList<Pair<Char, Int>>(numeratorLength + 4)
        for (i in 0 until numeratorLength) {
            chars.add(digit(258 + shift * 5 + i * 11) ?: return null)
        }
        val first = chars.joinToString("") { it.first.toString() }.toIntOrNull() ?: return null
        chars.clear()
        for (i in 0 until 4) {
            chars.add(digit(316 - shift * 5 + i * 11) ?: return null)
        }
        val maximum = chars.joinToString("") { it.first.toString() }.toIntOrNull() ?: return null
        if (maximum < 100 || first > maximum) return null
        return HudNumericValue(
            current = first,
            maximum = maximum,
            percent = (first * 100.0 / maximum).roundToInt().coerceIn(0, 100),
            matchError = chars.sumOf { it.second }
        )
    }
}
