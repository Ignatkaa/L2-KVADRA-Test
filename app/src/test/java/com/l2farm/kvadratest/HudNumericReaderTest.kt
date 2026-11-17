package com.l2farm.kvadratest

import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream
import org.junit.Assert.*
import org.junit.Test

/** Bit-packed white-digit masks extracted from the actual 2000x1110 PNGs. */
class HudNumericReaderTest {
    private fun fixture(base64: String): (Int, Int) -> IntArray? {
        val compressed = Base64.getDecoder().decode(base64)
        val packed = InflaterInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        assertEquals(1015, packed.size)
        return { x, y ->
            if (x !in 250..365 || y !in 78..147) null
            else {
                val p = (y - 78) * 116 + (x - 250)
                val bit = (packed[p / 8].toInt() ushr (7 - p % 8)) and 1
                if (bit != 0) intArrayOf(220, 220, 220)
                else intArrayOf(0, 0, 0)
            }
        }
    }

    @Test fun damagedPlayerHpUsesNumbersNotAnimatedBar() {
        // L2Meta_capture_1794952140845.png: HP 1109/3063, CP 2144/2144, MP 1416/1416
        val pixels = fixture("eNrdkz1uGzEQhR93FlwCIbQEDAQsBGfzU6YYIY0KwRaSJsfgwoGqFEqXKqHgIJWQs+QIsxaQa2SPsKUKw5vZyI19BE9DEt88gnwzAzyM3iMz8orAHrjcybxCrPptBQHcimRrQDW2c80VefsMDOaaJujL7jeByPAbhVn2Z2jQ8Fm5BoJ3N2xhqeAfAKHzV1hD+INTmDz/4RKR/OIX4HEYRxF9wPh3gMkesnRIep4BS4gnk2dgUj3dKuSIUWGJpxTFaTH3uzSvrweT7+rdcAucU7dvc8Biz0ig5nPcRDU+vIed8ju71hK+tkkhGh82scCKN/3XCR5smxMd7PdKJshXRwviTbxXmrzGO9WflCl6vfaTK/7DnyZnLMr2BKOwQxUPJ6UcL0yu++OLWvB0wyJxnaOR6KnXWZlVJpu8MrteLTlHPzCCleWFTg6a56bQRvdlTlkdoiYkHQf5aN0EX5lqKOBdbrWeRdmEL+pq5+x88vbGWFfCs7TXCushJBEc7mxkhePOOg9/2acJIoWXWhXx1k/KwtAx6gTl9G2CTZhpB4krKT/6yT+yI3N5")
        assertEquals(2144, HudNumericReader.read(HudStatRow.CP, pixels)?.current)
        val hp = HudNumericReader.read(HudStatRow.HP, pixels)
        assertEquals(1109, hp?.current)
        assertEquals(3063, hp?.maximum)
        assertEquals(36, hp?.percent)
        assertEquals(100, HudNumericReader.read(HudStatRow.MP, pixels)?.percent)
    }

    @Test fun threeDigitCpAndPartialHpAreParsed() {
        // L2Meta_capture_1794950557597.png: CP 940/2144; HP 2404/3063
        val pixels = fixture("eNrdk7GOEzEQhn+vV7OWMFlfg1xEyeYiUTuiSRFx4ZAQj0A5Cyi00FFxjk5KdcWVPI4vaXkII14gVKSIcoyvg0e4kdfy7OeZXc0/A/xrDdy87FyrKO5RN1njOECWt/D8eYsOr/JIC1Sb1gYCkeJ3Al13u8MSzM+fPsAzE2rURPxd4PCXWyMh8ZuzkvY0McHAk/ngy0dX82tEjX4zSeLR1Y/gwTT+cypwOd8qWPFVgUNFHFQicIls4RYVxpkJEY/JannUw6rK6UBtDCqe2vX+CMzizxvRCbObAAbsyC/3GtpdguTy3YSeIGBKXKA5d32osAir/LWoMqUpWO/oqpFqhsvwXhTUYeUlkmxPr0XBF7SMEpnubz8draT9aOQfPH5vmhgxq3uBOlY+iYKN35XIi9QdtIptPozbhMdr0nWhjV4lb3UG8qBRUcWFWmep1wh5H+AozV9auds9UxU0bB1ZWrPSnWMZnfSWTIHnqtlXsCb2omdVd+6LVPXO0FASdVtFpoYNqb8W2O4dp4TdiXwQeL8mY2EvMhcIdhNRJVmyJbJS+uBlWCJ/K7BzA+mgZGr9/3T8BbI9cl8=")
        assertEquals(940, HudNumericReader.read(HudStatRow.CP, pixels)?.current)
        assertEquals(44, HudNumericReader.read(HudStatRow.CP, pixels)?.percent)
        assertEquals(2404, HudNumericReader.read(HudStatRow.HP, pixels)?.current)
        assertEquals(78, HudNumericReader.read(HudStatRow.HP, pixels)?.percent)
    }

    @Test fun nearlyFullHpIsParsed() {
        // L2Meta_capture_1794951206409.png: HP 3027/3063
        val pixels = fixture("eNrdkz2O1EAQhV+7rXZLlMYtrYQ6GIH5CQlqRDLBaMeCZI/Ro0UbDxkRlAWCZMRZOEJ7RyLaO2Bu4HCC1ZhyyB5hKyt9/Vpd73UB/9dAEIZsLJiAbZeXFWI17CtkwG9s3hvYGvulns35zRMwmGs7Qyr7XxbWGn6tUPLhAg0avihbIJC/ZQdnC/4GWPR0jRaZ33uFifg3l4iWVj8BwnGactYHTH9GGCHktUfSfgGskckaWYCt6u29Qo6YFJZ4TKXTNFuBAQrt7Omy/nEycq678R4w31dkJGB1YCQ1/og2qvHhHZzCQyjVDrxyaYZjj5YLbPhm+ATUFKlQL4/uS6WRxb6T5GD5JqoSd0EjavHWtaJK6p2ardd+8PMb7lgbwarczTBeL414VPE4K7f27zkaqYfT8zrj8ZZD4lqiyZHsoLuyqIwY2ZhuUEueYRgZweX1pW4Omqem0I9OpSTRFG0Tkq5DvnJ+hi9NNRYgLzvNsyib8FFd7b1b6kXNrXG+BHHefVVYjyHljOPZRVY4dc4TaDukGSKFFyKak6NZWRh7irpBkj7PsAkL/UHZl1YeTPIP2Mh0ew==")
        assertEquals(3027, HudNumericReader.read(HudStatRow.HP, pixels)?.current)
        assertEquals(3063, HudNumericReader.read(HudStatRow.HP, pixels)?.maximum)
        assertEquals(99, HudNumericReader.read(HudStatRow.HP, pixels)?.percent)
    }

    @Test fun blankHudReturnsUnknown() {
        assertNull(HudNumericReader.read(HudStatRow.HP) { _,_ -> intArrayOf(0, 0, 0) })
    }
}
