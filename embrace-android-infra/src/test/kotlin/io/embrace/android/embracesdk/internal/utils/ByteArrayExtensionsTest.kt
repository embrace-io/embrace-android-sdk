package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ByteArrayExtensionsTest {

    private val buffer = "abcabcabd".toByteArray()

    @Test
    fun `indexOf finds the first occurrence starting from fromIndex`() {
        assertEquals(0, buffer.indexOf("abc".toByteArray()))
        assertEquals(3, buffer.indexOf("abc".toByteArray(), 1))
        assertEquals(6, buffer.indexOf("abd".toByteArray()))
        assertEquals(8, buffer.indexOf("d".toByteArray()))
        assertEquals(0, buffer.indexOf("abc".toByteArray(), -5))
    }

    @Test
    fun `indexOf returns -1 for absent, empty, oversized, or exhausted searches`() {
        assertEquals(-1, buffer.indexOf("abe".toByteArray()))
        assertEquals(-1, buffer.indexOf(ByteArray(0)))
        assertEquals(-1, buffer.indexOf("abcabcabdx".toByteArray()))
        assertEquals(-1, buffer.indexOf("abd".toByteArray(), 7))
        assertEquals(-1, ByteArray(0).indexOf("a".toByteArray()))
        // a partial match at the very end must not read past the array
        assertEquals(-1, buffer.indexOf("abdz".toByteArray()))
    }

    @Test
    fun `regionMatches works and does not look beyond the boundary of the array`() {
        assertTrue(buffer.regionMatches(3, "abc".toByteArray()))
        assertTrue(buffer.regionMatches(6, "abd".toByteArray()))
        assertTrue(buffer.regionMatches(0, ByteArray(0)))
        assertFalse(buffer.regionMatches(3, "abd".toByteArray()))
        assertFalse(buffer.regionMatches(7, "abd".toByteArray()))
        assertFalse(buffer.regionMatches(-1, "a".toByteArray()))
    }
}
