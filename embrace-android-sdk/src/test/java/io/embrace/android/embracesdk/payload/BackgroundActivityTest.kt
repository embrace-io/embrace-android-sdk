package io.embrace.android.embracesdk.payload

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BackgroundActivityTest {

    private val info = Session(
        sessionId = "fake-session-id",
        startTime = 123456789L,
        appState = "foreground",
        endTime = 987654321L,
        number = 5,
        messageType = "fake-message-type",
        lastHeartbeatTime = 123456780L,
        isColdStart = true,
        eventIds = listOf("fake-event-id"),
        infoLogIds = listOf("fake-info-id"),
        warningLogIds = listOf("fake-warn-id"),
        errorLogIds = listOf("fake-err-id"),
        infoLogsAttemptedToSend = 1,
        warnLogsAttemptedToSend = 2,
        errorLogsAttemptedToSend = 3,
        exceptionError = LegacyExceptionError(),
        crashReportId = "fake-crash-id",
        endType = Session.LifeEventType.BKGND_STATE,
        startType = Session.LifeEventType.BKGND_STATE,
        properties = mapOf("fake-key" to "fake-value"),
        unhandledExceptions = 1
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("bg_activity_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<Session>("bg_activity_expected.json")
        assertNotNull(obj)

        with(obj) {
            assertEquals("fake-session-id", sessionId)
            assertEquals(123456789L, startTime)
            assertEquals(987654321L, endTime)
            assertEquals(5, number)
            assertEquals("foreground", appState)
            assertEquals("fake-message-type", messageType)
            assertEquals(123456780L, lastHeartbeatTime)
            assertTrue(checkNotNull(isColdStart))
            assertEquals(listOf("fake-event-id"), eventIds)
            assertEquals(listOf("fake-info-id"), infoLogIds)
            assertEquals(listOf("fake-warn-id"), warningLogIds)
            assertEquals(listOf("fake-err-id"), errorLogIds)
            assertEquals(1, infoLogsAttemptedToSend)
            assertEquals(2, warnLogsAttemptedToSend)
            assertEquals(3, errorLogsAttemptedToSend)
            assertEquals("fake-crash-id", crashReportId)
            assertEquals(Session.LifeEventType.BKGND_STATE, endType)
            assertEquals(Session.LifeEventType.BKGND_STATE, startType)
            assertEquals(1, unhandledExceptions)
            assertEquals(LegacyExceptionError(), exceptionError)
            assertEquals(mapOf("fake-key" to "fake-value"), properties)
        }
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<Session>()
    }
}
