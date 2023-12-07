package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class PushNotificationBreadcrumbTest {

    private val info = PushNotificationBreadcrumb(
        "title",
        "body",
        "from",
        "id",
        1,
        "type",
        1600000000
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("push_notification_breadcrumb_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<PushNotificationBreadcrumb>("push_notification_breadcrumb_expected.json")
        assertEquals("title", obj.title)
        assertEquals("body", obj.body)
        assertEquals("from", obj.from)
        assertEquals("id", obj.id)
        assertEquals(1, obj.priority)
        assertEquals("type", obj.type)
        assertEquals(1600000000, obj.getStartTime())
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<PushNotificationBreadcrumb>()
        assertNotNull(obj)
    }
}
