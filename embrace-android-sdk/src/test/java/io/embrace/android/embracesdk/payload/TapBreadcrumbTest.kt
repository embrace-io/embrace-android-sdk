package io.embrace.android.embracesdk.payload

import android.util.Pair
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class TapBreadcrumbTest {

    private val serializer = EmbraceSerializer()

    private val info = TapBreadcrumb(
        Pair(0f, 0f),
        "tappedElementName",
        1600000000,
        TapBreadcrumb.TapBreadcrumbType.TAP
    )

    @Test
    fun testSerialization() {
        val expectedInfo = ResourceReader.readResourceAsText("tap_breadcrumb_expected.json")
            .filter { !it.isWhitespace() }
        val observed = serializer.toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("tap_breadcrumb_expected.json")
        val obj = serializer.fromJson(json, TapBreadcrumb::class.java)
        assertEquals("0,0", obj.location)
        assertEquals("tappedElementName", obj.tappedElementName)
        assertEquals(1600000000, obj.getStartTime())
        assertEquals(TapBreadcrumb.TapBreadcrumbType.TAP, obj.type)
    }

    @Test
    fun testEmptyObject() {
        val info = serializer.fromJson("{}", TapBreadcrumb::class.java)
        assertNotNull(info)
    }
}
