package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.utils.MessageUtils.boolToStr
import io.embrace.android.embracesdk.internal.utils.MessageUtils.withMap
import io.embrace.android.embracesdk.internal.utils.MessageUtils.withNull
import io.embrace.android.embracesdk.internal.utils.MessageUtils.withSet
import org.junit.Assert.assertEquals
import org.junit.Test

internal class MessageUtilsTest {

    @Test
    fun `true to string`() {
        assertEquals("true", boolToStr(true))
    }

    @Test
    fun `false to string`() {
        assertEquals("false", boolToStr(false))
    }

    @Test
    fun `withNull with a null integer`() {
        assertEquals("null", withNull(null as Int?))
    }

    @Test
    fun `withNull with a null string`() {
        assertEquals("null", withNull(null as String?))
    }

    @Test
    fun `withNull with a null long`() {
        assertEquals("null", withNull(null as Long?))
    }

    @Test
    fun `withNull with a long value`() {
        assertEquals("\"10\"", withNull(10L))
    }

    @Test
    fun `withNull with an int value`() {
        assertEquals("\"10\"", withNull(10))
    }

    @Test
    fun `withNull with a string value`() {
        assertEquals("\"value\"", withNull("value"))
    }

    @Test
    fun `withSet with a null value`() {
        assertEquals("[]", withSet(null))
    }

    @Test
    fun `withSet with an empty value`() {
        assertEquals("[]", withSet(setOf()))
    }

    @Test
    fun `withSet with a set of null values`() {
        assertEquals("[null,\"null\"]", withSet(setOf(null, "null")))
    }

    @Test
    fun `withSet with a set of values`() {
        assertEquals("[\"10\",\"20\",\"stringValue\"]", withSet(setOf("10", "20", "stringValue")))
    }

    @Test
    fun `withMap with a null value`() {
        assertEquals("{}", withMap(null))
    }

    @Test
    fun `withMap with an empty value`() {
        assertEquals("{}", withMap(mapOf()))
    }

    @Test
    fun `withMap with a map of null values`() {
        assertEquals("{null: null}", withMap(mapOf(null to null)))
    }

    @Test
    fun `withMap with a map of values`() {
        assertEquals(
            "{\"10\": \"20\",\"another\": \"value\"}",
            withMap(mapOf("10" to "20", "another" to "value"))
        )
    }
}
