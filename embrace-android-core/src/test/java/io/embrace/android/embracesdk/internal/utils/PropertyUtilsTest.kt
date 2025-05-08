package io.embrace.android.embracesdk.internal.utils

import io.embrace.android.embracesdk.internal.utils.PropertyUtils.sanitizeProperties
import org.junit.Assert.assertEquals
import org.junit.Test

internal class PropertyUtilsTest {

    @Test
    fun testEmptyCase() {
        assertEquals(emptyMap<String, String>(), sanitizeProperties(null))
        assertEquals(emptyMap<String, String>(), sanitizeProperties(emptyMap()))
    }

    @Test
    fun testPropertyLimitExceeded() {
        val input = (0..20).associateBy { "$it" }
        assertEquals("PropertyUtils should not cap the attribute count", input.size, sanitizeProperties(input as Map<String, Any>?).size)
    }

    @Test
    fun testNullValue() {
        assertEquals("null", sanitizeProperties(mapOf("a" to null))["a"])
    }

    @Test
    fun testSerializableValue() {
        val obj = SerializableClass()
        assertEquals(obj, sanitizeProperties(mapOf("a" to obj))["a"])
    }

    @Test
    fun testUnserializableValue() {
        assertEquals("not serializable", sanitizeProperties(mapOf("a" to UnSerializableClass()))["a"])
    }

    private class SerializableClass : java.io.Serializable
    private class UnSerializableClass
}
