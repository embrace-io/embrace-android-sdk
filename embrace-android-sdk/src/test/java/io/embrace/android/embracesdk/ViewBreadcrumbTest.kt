package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.EmbraceSerializer
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
    private val serializer = EmbraceSerializer()

    @Test
    fun testSerialization() {
        val expectedInfo = ResourceReader.readResourceAsText("view_breadcrumb_expected.json")
            .filter { !it.isWhitespace() }
        val observed = serializer.toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("view_breadcrumb_expected.json")
        val obj = serializer.fromJson(json, ViewBreadcrumb::class.java)
        assertEquals("screen", obj.screen)
        assertEquals(1600000000L, obj.getStartTime())
        assertEquals(1700000000L, obj.end)
    }

    @Test
    fun testEmptyObject() {
        val info = serializer.fromJson("{}", ViewBreadcrumb::class.java)
        assertNotNull(info)
    }
}
