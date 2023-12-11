package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class RnActionBreadcrumbTest {

    private val info = RnActionBreadcrumb(
        "my_action",
        1600000000,
        1600000100,
        mapOf("key" to "value"),
        104,
        "test"
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("rn_action_breadcrumb_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<RnActionBreadcrumb>("rn_action_breadcrumb_expected.json")
        assertEquals("my_action", obj.name)
        assertEquals(1600000000, obj.getStartTime())
        assertEquals(1600000100, obj.endTime)
        assertEquals(mapOf("key" to "value"), obj.properties)
        assertEquals(104, obj.bytesSent)
        assertEquals("test", obj.output)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<RnActionBreadcrumb>()
        assertNotNull(obj)
    }
}
