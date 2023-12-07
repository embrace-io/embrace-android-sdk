package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CustomBreadcrumbTest {

    private val info = CustomBreadcrumb(
        "test",
        1600000000
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("custom_breadcrumb_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<CustomBreadcrumb>("custom_breadcrumb_expected.json")
        assertEquals("test", obj.message)
        assertEquals(1600000000, obj.getStartTime())
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<CustomBreadcrumb>()
        assertNotNull(obj)
    }
}
