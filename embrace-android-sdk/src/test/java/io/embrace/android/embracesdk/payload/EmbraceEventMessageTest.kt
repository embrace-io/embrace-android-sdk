package io.embrace.android.embracesdk.payload

import com.google.gson.Gson
import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.ResourceReader
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
        type = EmbraceEvent.Type.WARNING_LOG,
        logExceptionType = LogExceptionType.NONE.value,
        screenshotTaken = false,
        appState = "active",
        customProperties = mapOf("Float" to 1, "String" to "TestString"),
        sessionProperties = mapOf()
    )

    private val eventMessage = EventMessage(
        event = eventComplete,
        performanceInfo = PerformanceInfo(
            diskUsage = DiskUsage(appDiskUsage = null, deviceDiskFree = 3862863872L),
            powerSaveModeIntervals = listOf(PowerModeInterval(startTime = 1679580212117L)),
        ),
        userInfo = UserInfo(personas = setOf("first_day")),
        version = 13
    )

    @Test
    fun testMandatoryValues() {
        assertNotNull(eventMessage.event)
    }

    @Test
    fun testSerialization() {
        val data = ResourceReader.readResourceAsText("eventmessage_expected.json")
            .filter { !it.isWhitespace() }
        val observed = Gson().toJson(eventMessage)
        assertEquals(data, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("eventmessage_expected.json")
        val obj = Gson().fromJson(json, EventMessage::class.java)
        assertEquals("eventId", obj.event.eventId)
        assertEquals("sessionId", obj.event.sessionId)
        assertEquals("messageId", obj.event.messageId)

        assertEquals(13, obj.version)
        assertEquals(3862863872L, obj.performanceInfo?.diskUsage?.deviceDiskFree)
        assertEquals(1679580212117L, obj.performanceInfo?.powerSaveModeIntervals?.get(0)?.startTime)
        assertEquals(1, obj.userInfo?.personas?.size)
    }
}
