package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Test

internal class PropertyUtilsTest {

    @Test
    fun `no truncation`() {
        val value = "a".repeat(100)
        assertEquals(value, PropertyUtils.truncate(value, 100))
    }

    @Test
    fun `basic truncation`() {
        val value = "a".repeat(110)
        assertEquals(value.take(97) + "...", PropertyUtils.truncate(value, 100))
    }

    @Test
    fun `max length shorter than the ellipsis does not throw`() {
        assertEquals("ab", PropertyUtils.truncate("abcdef", 2))
        assertEquals("", PropertyUtils.truncate("abcdef", 0))
        assertEquals("", PropertyUtils.truncate("abcdef", -1))
    }

    @Test
    fun `surrogate pairs are not split`() {
        assertEquals("", PropertyUtils.truncate("😀😀", 1))
        assertEquals("😀", PropertyUtils.truncate("😀😀", 3))
        assertEquals("a...", PropertyUtils.truncate("a😀bcdef", 5))
    }
}
