package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.utils.PropertyUtils
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PropertiesTest {

    @Test
    fun testPropertiesNormalization() {
        val sourceMap: MutableMap<String?, Any> = HashMap()
        sourceMap[null] = "Null key"
        sourceMap[""] = "Empty key"
        sourceMap["EmptyValue"] = ""
        sourceMap["NullValue"] = ""
        for (i in 1..9) {
            sourceMap["Key$i"] = "Value$i"
        }
        val resultMap = PropertyUtils.sanitizeProperties(sourceMap, InternalEmbraceLogger())
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
}
