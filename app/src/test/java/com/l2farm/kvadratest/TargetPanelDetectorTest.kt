package com.l2farm.kvadratest

import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream
import org.junit.Assert.*
import org.junit.Test

/**
 * Gold-pixel masks extracted from three original game screenshots:
 *   Ol Mahum Guard selected, Hot Springs Antelope selected, no selected target.
 * ROI: x=735..1494, y=13..110, dimensions 760x98.
 */
class TargetPanelDetectorTest {
    private fun actualPanelMask(compressed: String): (Int,Int) -> Int? {
        val bytes = InflaterInputStream(ByteArrayInputStream(
            Base64.getDecoder().decode(compressed)
        )).use { it.readBytes() }
        assertEquals(9310,bytes.size)
        return { x,y ->
            if (x !in 735..1494 || y !in 13..110) 0x122038
            else {
                val i=(y-13)*760+(x-735)
                if ((bytes[i/8].toInt() and (1 shl (7-i%8))) != 0)
                    0xB79649 else 0x122038
            }
        }
    }

    @Test fun recognizesLeftPanelFromOriginalOlMahumPng() {
        val pixel=actualPanelMask("eNrt1z1OwzAUAOAXXJFCqzaIpQhEK04ATEgMKT9i4hAdOEBHtlqwdMzIyBE4QhBLJS5hiZUBxMJQNaSJneKURDj2EwViqUutfHl9fn52AVCHFQTBa1BwPIUfBuDmv2KzKD8JH3YABjSXXynKP3OeZduDIHgvmlk7fIUHkAd0NdfOiyL0kXgIeTdaYEx+kskzrdz4IU/CJciY3z1ydHjS8SAv+f3jqg6/7Mzx0iZjp3Udvl7lPJ21AYk/t4Piujue44lURexspBN95WbKu5+S07Al/uRFh1968eLdG/LxD2hJ8/6RXmEyL267SV84kPmur7WrpnU/TX6Sg740zTR5GvFtV/BETkbPCL/cFsmxZa5rhIf2BOLt2Uz3HDO8KPxteXpkhm8K/lKerprhieAZRnIsynnLR+FF0ISi8OK0sg0fhjz3ouu2EM7aKc+TMnf0+Ub5dLBXZniX8+mVJQo84Xe/cXQ+RYPCncSnx4MC30xurpa4ZTK4zeXvqToffOZ7ubzK2EqKO0mpaPNZfK0AT2iyOfs8+kFGjh8V+NZXfHz47WUtbTE+uUOmztb0uP7VPMHl18vofyz6Red7Jb+gfG2xol9V4w8V+TU1/hqXV01Oraz7H+PbuPyscno2Ku9VKSY/XEON/mIn4jtI/P4pKr8xROXrDhJvRU9VAJfvoPLIyUFb2phHK8yYR9tWMc+bAhbvrXyDfyvM84aMFT0APt/5XXwDl4eS/4u8U/Ilr8lbsy/N8ZDwgMnDf+GXcHnkYVFNIP+/1QewwK+B")
        val found=TargetPanelDetector.detect(2000,1110,pixel)
        assertTrue(found.visible)
        assertEquals(TargetBarPosition.LEFT_755,found.position)
        assertTrue(found.left.valid)
        assertFalse(found.right.valid)
    }

    @Test fun recognizesRightPanelFromOriginalHotSpringsPng() {
        val pixel=actualPanelMask("eNrt2U1rG0cYB/D/SIq0pbKiJKZSihrNpkbNoSRTUxK5Vu216UEUFUx76SEUFUJPKZgWGoUkeO2IUCiUHnow9NJCP0COgfawhrTk1G9QEMXQ6/aFRBRV22dXu9bqxTbSzrgxZMBGaNnfzj4z88wzCPj/muM4bWfK9gf92Yc+YWZa/gndrLlCNqAKe+gp9wp3PyWm5R+P8IkQD5dfdpzOtIGN0yMe7cOzHs8jjl31gN6bnMng2b7BUc6vR9Jb+/HM5z+PorOvDuH1ZhQ+rvWHdmnwwT3+fDIKn0z0+eWBK37sS3HHnFovdkM8HxrarMu/PBel97Ha/vwaIJjejjS07f7QcrQGeMubOXakiWmH+JQ9ZmLWI/H1akB5fGWQF1F548h4zNgD+V42/2vAmyp45ijl407Lj70SHjeglP+LeENd7H8IeCUzh90HrhsKNkN/IVF6SavjoZinXbswHPu6BN6fOQKxEb41QXL3a78u3NrOaxbel8angtrSq6u9ZrsRCXhjdGK6e8rEvBniRZ9fA/txKCFPEuZMUH1Ye+/c6FPelc5wvjcn55nFgncuIy+NT4/jyyGepyTxwV35Af7Mk93hrTw6vxw6pnU2vCsbXt7c6EBK7w9YKseF11TxOUqLJ50uL7LthAK+aLr56B7PsK4K/kXqfezyzzyT+LipgO8VsB+JlFZ/KJ9PZ2/SB/5nGdmVXfl8/sItWv5rj8vg3yngRWOeCnH7msBK65F8ntco+Mb2nMCmWFPA380BK8lLFWylr8rnP7z7BfGpFyr1rcxcRP6EOcqf2ASs5++JHTMXWlZ8Gl4b5Y3CT5R9F77Ud/DebMTej+FXS8Q33/lF39S+mZUfnFX9JPGrH8wzrfm67KHN492VIvD91jnBqrMK+HMlmpiV2iXBGts1+fxSgfjb5RKPNdaj8ilzhH8TxHdR4vG/UZM2c0Tc7PG3segeHS5zqt3k8dWk4fPmYpaODleE3sJ1CTw8/lra630WG+ZiPv0vPiuzf7ArjZ8/ix5fsRZOaw18UqFXkMefnff5wrfPUYyM0xzCnJPGp3d9PtM5lcdLjRLHG/Zb0vik5vMzv+XOmPGHF19BTrsqjY/lfR51/UoL1tsXLdb8WgGv8wrHqvGqgSbkB4fWQYWexqjiiUH+0NKXbqJkF0ZuMGVMTPqSggNWllAhj1lW9N87v8nl95ICZbcY9V5PSOWrSavHa3QAFBr4+f5PKBJ4PyFTUsD6g/t56K+NO4RPw5uh7SSLzkLrwVL70x1xx/7dlM873VQB8WK3jKLTlsIjzB/YJt1roZTHM34SPvEsOEfAJ441jyPns2r4flPO82PWezw1PJ5u/pBmRbxf4D/kjzHi")
        val found=TargetPanelDetector.detect(2000,1110,pixel)
        assertTrue(found.visible)
        assertEquals(TargetBarPosition.RIGHT_980,found.position)
        assertFalse(found.left.valid)
        assertTrue(found.right.valid)
    }

    @Test fun realPausePngDoesNotInventTargetPanel() {
        val pixel=actualPanelMask("eNrt07ENwjAQRmGfsYCCgoqSULADFSIS9KzgTQgTsBKjpEtJSwVsYKOc/gL0vvaSl8SOQwAAAAAAAAAAoGw+eviVZWm4tk6ZX9lduXIH6133x/L4Is5fn658CsW9201eyrW35iE9FbdBmj8PnTLfvH35TXm8d+Wtz5UrZq6Xb3PlVByFeQsh+/YuO+e11/dtvZvuv0/a/EKfN92yT7W7GrWPiPovIF+w1eZP2nzS5tuAP9X9dH6sD7tmF1M=")
        val found=TargetPanelDetector.detect(2000,1110,pixel)
        assertFalse(found.visible)
        assertNull(found.position)
        assertFalse(found.left.valid)
        assertFalse(found.right.valid)
    }

    @Test fun darkBackgroundAndSingleGoldLineCannotFakeASelectedTarget() {
        assertFalse(TargetPanelDetector.detect(2000,1110) { _,_ -> 0x10151A }.visible)
        val horizontalOnly: (Int,Int) -> Int? = { x,y ->
            if (x in 741..1259 && y in 14..16) 0xB79649 else 0x10151A
        }
        assertFalse(TargetPanelDetector.detect(2000,1110,horizontalOnly).visible)
    }
}
