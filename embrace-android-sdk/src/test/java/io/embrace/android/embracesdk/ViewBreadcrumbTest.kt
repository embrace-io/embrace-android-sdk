package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.ViewBreadcrumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class ViewBreadcrumbTest {

    private val info = ViewBreadcrumb(
        "screen",
        1600000000
    ).apply {
        end = 1700000000
    }

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("view_breadcrumb_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<ViewBreadcrumb>("view_breadcrumb_expected.json")
        assertEquals("screen", obj.screen)
        assertEquals(1600000000L, obj.getStartTime())
        assertEquals(1700000000L, obj.end)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<ViewBreadcrumb>()
        assertNotNull(obj)
    }
}
