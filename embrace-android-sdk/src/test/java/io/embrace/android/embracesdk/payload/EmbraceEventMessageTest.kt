package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.internal.EventType
import io.embrace.android.embracesdk.internal.payload.Event
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class EmbraceEventMessageTest {

    private val eventComplete = Event(
        eventId = "eventId",
        sessionId = "sessionId",
        messageId = "messageId",
        name = "test",
        timestamp = 1111L,
        type = EventType.WARNING_LOG,
        logExceptionType = LogExceptionType.NONE.value,
        screenshotTaken = false,
        appState = "foreground",
        customProperties = mapOf("Float" to 1, "String" to "TestString"),
        sessionProperties = mapOf()
    )

    private val eventMessage = EventMessage(
        event = eventComplete,
        userInfo = UserInfo(personas = setOf("first_day")),
        version = 13
    )

    @Test
    fun testMandatoryValues() {
        assertNotNull(eventMessage.event)
    }

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("eventmessage_expected.json", eventMessage)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<EventMessage>("eventmessage_expected.json")
        assertEquals("eventId", obj.event.eventId)
        assertEquals("sessionId", obj.event.sessionId)
        assertEquals("messageId", obj.event.messageId)

        assertEquals(13, obj.version)
        assertEquals(1, obj.userInfo?.personas?.size)
    }
}
