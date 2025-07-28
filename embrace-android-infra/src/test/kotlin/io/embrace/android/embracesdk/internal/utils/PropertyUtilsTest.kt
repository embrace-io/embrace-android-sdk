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
}
