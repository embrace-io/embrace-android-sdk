package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class PropertiesTest {

    @Test
    fun testPropertiesNormalization() {
        val sourceMap: MutableMap<String, Any> = HashMap()
        sourceMap[""] = "Empty key"
        sourceMap["EmptyValue"] = ""
        sourceMap["NullValue"] = ""
        for (i in 1..9) {
            sourceMap["Key$i"] = "Value$i"
        }
        val resultMap = PropertyUtils.sanitizeProperties(sourceMap)
        assertEquals(
            "Unexpected normalized map size.",
            sourceMap.size,
            resultMap.size
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
}
