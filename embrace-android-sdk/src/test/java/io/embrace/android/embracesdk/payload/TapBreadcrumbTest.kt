package io.embrace.android.embracesdk.payload

import android.util.Pair
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class TapBreadcrumbTest {

    private val info = TapBreadcrumb(
        Pair(0f, 0f),
        "tappedElementName",
        1600000000,
        TapBreadcrumb.TapBreadcrumbType.TAP
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("tap_breadcrumb_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<TapBreadcrumb>("tap_breadcrumb_expected.json")
        assertEquals("0,0", obj.location)
        assertEquals("tappedElementName", obj.tappedElementName)
        assertEquals(1600000000, obj.getStartTime())
        assertEquals(TapBreadcrumb.TapBreadcrumbType.TAP, obj.type)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<TapBreadcrumb>()
        assertNotNull(obj)
    }
}
