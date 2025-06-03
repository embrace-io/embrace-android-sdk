package io.embrace.android.embracesdk.internal.utils

import io.embrace.android.embracesdk.internal.utils.PropertyUtils.sanitizeProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
        val expected = (0..9).associateBy { "$it" }
        assertEquals(expected, sanitizeProperties(input as Map<String, Any>?))
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

    @Test
    fun `bypass limits`() {
        val input = (0..20).associateBy { "$it" }
        assertEquals(input.size, sanitizeProperties(input, true).size)
    }

    @Test
    fun testPropertiesNormalization() {
        val sourceMap: MutableMap<String, Any> = HashMap()
        sourceMap[""] = "Empty key"
        sourceMap["EmptyValue"] = ""
        sourceMap["NullValue"] = ""
        for (i in 1..9) {
            sourceMap["Key$i"] = "Value$i"
        }
        val resultMap = sanitizeProperties(sourceMap)
        assertTrue(
            "Unexpected normalized map size.",
            resultMap.size <= PropertyUtils.MAX_PROPERTY_SIZE
        )
        resultMap.entries.stream()
            .peek { (key): Map.Entry<String, Any> ->
                assertNotNull(
                    "Unexpected normalized map key: NULL.",
                    key
                )
            }
            .peek { (_, value): Map.Entry<String, Any> ->
                assertNotNull(
                    "Unexpected normalized map value: NULL.",
                    value
                )
            }
    }

    private class SerializableClass : java.io.Serializable
    private class UnSerializableClass
}
