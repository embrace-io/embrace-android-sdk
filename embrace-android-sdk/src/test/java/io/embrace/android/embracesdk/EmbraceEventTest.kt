package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.payload.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class EmbraceEventTest {

    private val event = Event(
        eventId = Uuid.getEmbUuid(),
        timestamp = 100L,
        type = EmbraceEvent.Type.CRASH
    )

    private val eventComplete = Event(
        eventId = "eventId",
        sessionId = "sessionId",
        messageId = "messageId",
        name = "test",
        timestamp = 1111L,
        type = EmbraceEvent.Type.WARNING_LOG,
        logExceptionType = LogExceptionType.NONE.value,
        screenshotTaken = false,
        appState = "active",
        customProperties = mapOf("Float" to 1, "String" to "TestString"),
        sessionProperties = mapOf()
    )

    @Test
    fun testMandatoryValues() {
        assertNotNull(event.eventId)
        assertNotNull(event.timestamp)
        assertNotNull(event.type)
    }

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("event_expected.json", eventComplete)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<Event>("event_expected.json")
        assertEquals("eventId", obj.eventId)
        assertEquals("sessionId", obj.sessionId)
        assertEquals("messageId", obj.messageId)
        assertEquals("test", obj.name)
        assertEquals(1111L, obj.timestamp)
        assertEquals(EmbraceEvent.Type.WARNING_LOG, obj.type)
        assertEquals(LogExceptionType.NONE.value, obj.logExceptionType)
        assertEquals(false, obj.screenshotTaken)
        assertEquals("active", obj.appState)
        assertEquals(mapOf("Float" to 1.0, "String" to "TestString"), obj.customProperties)
        assertEquals(mapOf<String, Any>(), obj.sessionProperties)
    }
}
