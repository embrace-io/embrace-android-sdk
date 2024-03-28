package io.embrace.android.embracesdk.utils

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.utils.PropertyUtils.sanitizeProperties
import org.junit.Assert.assertEquals
import org.junit.Test

internal class PropertyUtilsTest {

    private val logger = InternalEmbraceLogger()

    @Test
    fun testEmptyCase() {
        assertEquals(emptyMap<String, String>(), sanitizeProperties(null, logger))
        assertEquals(emptyMap<String, String>(), sanitizeProperties(emptyMap(), logger))
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testPropertyLimitExceeded() {
        val input = (0..20).associateBy { "$it" }
        val expected = (0..9).associateBy { "$it" }
        assertEquals(expected, sanitizeProperties(input as Map<String?, Any>?, logger))
    }

    @Test
    fun testNullValue() {
        assertEquals("null", sanitizeProperties(mapOf("a" to null), logger)["a"])
    }

    @Test
    fun testSerializableValue() {
        val obj = SerializableClass()
        assertEquals(obj, sanitizeProperties(mapOf("a" to obj), logger)["a"])
    }

    @Test
    fun testUnserializableValue() {
        assertEquals("not serializable", sanitizeProperties(mapOf("a" to UnSerializableClass()), logger)["a"])
    }

    private class SerializableClass : java.io.Serializable
    private class UnSerializableClass
}
