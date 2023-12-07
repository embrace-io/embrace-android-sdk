package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.FragmentBreadcrumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class FragmentBreadcrumbTest {

    private val info = FragmentBreadcrumb(
        "test",
        1600000000,
        1600001000,
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("fragment_breadcrumb_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<FragmentBreadcrumb>("fragment_breadcrumb_expected.json")
        assertEquals("test", obj.name)
        assertEquals(1600000000, obj.getStartTime())
        assertEquals(1600001000, obj.endTime)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<FragmentBreadcrumb>()
        assertNotNull(obj)
    }
}
