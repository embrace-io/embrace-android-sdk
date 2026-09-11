package io.embrace.analysis.common.text

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Python-parity formatting: exact-binary half-to-even rounding (Java's `%.2f` rounds half-up and
 * disagrees only on ties), Python's negative-zero sign, and its nan/inf spellings.
 */
class PyFormatTest {

    @Test
    fun `fixed rounds the exact binary value half to even on a tie`() {
        assertEquals("0.12", PyFormat.fixed(0.125, 2))
        assertEquals("0.38", PyFormat.fixed(0.375, 2))
    }

    @Test
    fun `fixed keeps the sign of a negative value that rounds to zero`() {
        assertEquals("-0.00", PyFormat.fixed(-0.001, 2))
        assertEquals("-0.0", PyFormat.fixed(-0.0, 1))
        assertEquals("0.0", PyFormat.fixed(0.0, 1))
    }

    @Test
    fun `fixed prints nan and both infinities as Python does`() {
        assertEquals("nan", PyFormat.fixed(Double.NaN, 2))
        assertEquals("inf", PyFormat.fixed(Double.POSITIVE_INFINITY, 2))
        assertEquals("-inf", PyFormat.fixed(Double.NEGATIVE_INFINITY, 2))
    }

    @Test
    fun `fixedSigned always signs except nan, which stays unsigned`() {
        assertEquals("+3.5", PyFormat.fixedSigned(3.5, 1))
        assertEquals("-3.5", PyFormat.fixedSigned(-3.5, 1))
        assertEquals("+0.0", PyFormat.fixedSigned(0.0, 1))
        assertEquals("nan", PyFormat.fixedSigned(Double.NaN, 1))
    }

    @Test
    fun `percent0 scales to a whole percentage with half to even rounding and a kept sign`() {
        assertEquals("12%", PyFormat.percent0(0.125))
        assertEquals("-0%", PyFormat.percent0(-0.001))
    }
}
