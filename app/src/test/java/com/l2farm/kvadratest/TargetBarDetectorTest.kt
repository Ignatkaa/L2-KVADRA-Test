package com.l2farm.kvadratest

import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Real red-pixel masks cropped from user-supplied game PNGs (x=740..1330,y=60..103). */
class TargetBarDetectorTest {
    private fun actualPngMask(base64: String): (Int,Int)->Boolean {
        val decoded = Base64.getDecoder().decode(base64)
        val bits = InflaterInputStream(ByteArrayInputStream(decoded)).use { it.readBytes() }
        assertEquals(3251,bits.size)
        return { x,y ->
            if (x !in 740..1330 || y !in 60..103) false
            else {
                val index = (y-60)*591+(x-740)
                (bits[index/8].toInt() and (1 shl (7-index%8))) != 0
            }
        }
    }

    @Test fun leftPanel68Percent() {
        val red=actualPngMask("eNrtlEEKwjAQRUcaqotSXXYh9CKCV/EY7hLwYgEP0CvUG2SZRYlCwlQhDThMlvOXs3hkPi8DIJFI/sv+XQ6NdCyDAo00lkkLjXTNAG7o+sj3NJLOSAbgEPmORtrs+RT5Mwm022wnTS2J1GQk3+LUMHVyCt/E1cnidmydDO7M1mltj6tT6LA9rk5haPvUHl8ndUk7s3UCuN80/SzkOnkFL1tFJ39uno8qOi16nKYqOv0WxrtO3//HvU5rZpBIJPXzAXYMJV0=")
        val result=TargetBarDetector.detect(2000,1110,red)
        assertEquals(TargetBarPosition.LEFT_755,result.position)
        assertTrue("HP ${result.percent}",abs(result.percent!!-68)<=3)
    }

    @Test fun leftPanel92Percent() {
        val red=actualPngMask("eNrtlTEOwjAMAA0ZylABI0MlvsDYAYk+pc9gQGokPuaKB/QL6Q8yZkANu53BWBl9Y6ScEuuUABiGIaPJAjaJ6SQxfSWmq8SUJKYH2xae3ZH4o8Q0MRMMcCB+lJiK0z0TvxeIdsWZ0FWJyTFTHC5sVZdTmCM9kzIndEhNypywYXdW5uS7lpqUOfnbnU4vqHLKYdzo9FCVU579i95Zl1PaQz9O/z8rPKfYwopVcgo9fN5VclqTW5YqORVQ5lRAmVMBtH/TMKrxA/Ki/Js=")
        val result=TargetBarDetector.detect(2000,1110,red)
        assertEquals(TargetBarPosition.LEFT_755,result.position)
        assertTrue("HP ${result.percent}",abs(result.percent!!-92)<=3)
    }

    @Test fun shiftedRightPanel100Percent() {
        val red=actualPngMask("eNrtlTGL1EAUx/8vM2S2CNktUyyb+AlMmeIkIzaWVmK5hYh2oiAHFjurIJaWlvcR7gMIRq44OzuxEC4LloKxSxH3nNlbFW7m3IFp9xUJDO/93su8/3tBzNQDzJj6jgOEWSJefkEW0zHKQNJozFpMRpHEw0AS0nEDTLkK5URgtX5VT5GFoiCegOPWfYwCORLiG14jvU1vw2v6CIVsQsEgyt+om5G8hleBIIb8EWIu7yIJ7t3sM/iI3slpECcGpxuDbhvrcc+u99zP1kbgKNjzHncgehzbvfAkDdp3gjmJFg2uN3RkkcaepF77FmjUj4bUyZli0iLlVoxYtodVffm0M7qk5eqTEvThPVL7Fq2QISVVFIvLx632VcTOzxCzZg1H6+wQM5tkVao3gNY1mz2TXPw6OHKMnR1SOjuqtjrAHGmpL91Wvx0yV4WjD9v+NHrqckWQHnLCiZl4l5xMf0xpC+4cbYu0jEnZx8OFe2UgnXM32Z/xImGOBP12PWmIKcuxVGw58Sx2JOg23n8hkYec1kmZOBK0f/5SBpKBe8hpyOZZZSfYtJ2bIU7M/PnIadBy6u0E//5KE/MoPeTUP+a0UlfIiQo9xtHm6nfLqWun4pRdISfSBBnxi6vfJadVezhOnXIyk5LqyZVfKy85nf4cFnXtltP/LPfcc91OUu1JaneSFp6kBnvb29687TetHWM+")
        val result=TargetBarDetector.detect(2000,1110,red)
        assertEquals(TargetBarPosition.RIGHT_980,result.position)
        assertTrue("HP ${result.percent}",abs(result.percent!!-100)<=3)
    }

    @Test fun shiftedRightPanel35Percent() {
        val red=actualPngMask("eNrtlTFLw0AYht/LhSRDaTJ2KGn8BXbsoPbExdFJHDuI6KqjBa8I4ujo2J/QHyAYcXERN3FrBEdBxwwl9WKLeCbeNdzad7gMRx7u8j3fFziUHyCk/B1rMEvNPX9BwyEjtA1Jnk8TBJ7FcGhIQt2PgabNTTkWaFc8OidomKLgHsPG1j48Qw6D+4ZL1LfJtfmZHsDRCIgxiLSu+KbFVnBhCKJoHcGx2S5qxrULn2F75IY1jTgObLI+EWWjKfaK553+zkQtOCJ6mmIHbopRsRYVSAF6xE0Q89WYDAu7vkT6VJIiAfmIyeB+zCkr7LYkUqL2kgxen7hL725RL+52JVKsJHFCp2M4bpyhpHRnEknZ4sJr6mTM9h/7w5K2k0BTvQfoIQRKzk4kUKYj+eKK2BCnY0Y6iYT50i9vbYmU6kidfClXpYpOmN2JW98fX62TjsSDOcTS6ZRomzjKIUwMYI1OajHz93v5YIqh1WmRf0+QL22NTvPyBX/qICsb/Xx6hU7ZIuNuVj+j6ZR3iiM6N651tDqlBpO5mk6qVNXp/1TSaZllllksXz1NhBY=")
        val result=TargetBarDetector.detect(2000,1110,red)
        assertEquals(TargetBarPosition.RIGHT_980,result.position)
        assertTrue("HP ${result.percent}",abs(result.percent!!-35)<=3)
    }

    @Test fun zeroHpStaysUnknownNotInventedZeroOrAlive() {
        val red=actualPngMask("eNrtlb9KA0EQxr+5PXavOJIrrwiX+ARaplBcsbG0EssTRLQTbQRTjAhiaWmZR8gDCK7a2Iid2CWCpaBlCkmcsxPTTcr7il1Y2B/z55tdWMN7KAx/YBk6pe78FbmlAZaUpKRpRsiSyGNfSUKjGYBWzFpOBLMqW/cYuRYFd4QY67tIlBwP945LNDboWh/TIxh5RmoQta94LfILuFCCDNoHsLHfQqruXfGCOKEb31JxLGJa+Za2mTG2dQZHx5yOsQk3xkBFylCSGyFgMVBfReog8Gcgfhiy8Tpf0tnbMzu6u0VD1zkmMx3CmjCBrnXia2MnPnZPvb527MQHZSmZ+aAelqakiEKi81pSUS09zEHdavmaA8jLKweOEqgfFc5aFSSXSVZaHPLLCeSkCk2Vm9w/5LQqVKKv1I6MMe5nfJs0/acQzTr9m6PSTr9VDgIhBisnBaWVKvk0R61atWqp9QPo22Kw")
        val result=TargetBarDetector.detect(2000,1110,red)
        assertNull(result.percent)
        assertNull(result.position)
    }
}
