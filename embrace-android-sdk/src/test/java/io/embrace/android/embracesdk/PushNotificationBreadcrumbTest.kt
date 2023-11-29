package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class PushNotificationBreadcrumbTest {

    private val serializer = EmbraceSerializer()

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
        val expectedInfo = ResourceReader.readResourceAsText("push_notification_breadcrumb_expected.json")
            .filter { !it.isWhitespace() }
        val observed = serializer.toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("push_notification_breadcrumb_expected.json")
        val obj = serializer.fromJson(json, PushNotificationBreadcrumb::class.java)
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
        val info = serializer.fromJson("{}", PushNotificationBreadcrumb::class.java)
        assertNotNull(info)
    }
}
