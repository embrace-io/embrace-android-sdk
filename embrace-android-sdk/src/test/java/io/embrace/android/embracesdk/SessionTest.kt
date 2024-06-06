package io.embrace.android.embracesdk

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SessionTest {

    private val info = Session(
        sessionId = "fake-session-id",
        startTime = 123456789L,
        endTime = 987654321L,
        number = 5,
        appState = "foreground",
        lastHeartbeatTime = 123456780L,
        isEndedCleanly = true,
        isReceivedTermination = true,
        isColdStart = true,
        terminationTime = 16090292309L,
        eventIds = listOf("fake-event-id"),
        infoLogIds = listOf("fake-info-id"),
        warningLogIds = listOf("fake-warn-id"),
        errorLogIds = listOf("fake-err-id"),
        networkLogIds = listOf("fake-network-id"),
        crashReportId = "fake-crash-id",
        endType = LifeEventType.STATE,
        startType = LifeEventType.STATE,
        startupDuration = 1223,
        startupThreshold = 5000,
        sdkStartupDuration = 109,
        properties = mapOf("fake-key" to "fake-value")
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("session_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<Session>("session_expected.json")
        assertNotNull(obj)

        with(obj) {
            assertEquals("fake-session-id", sessionId)
            assertEquals(123456789L, startTime)
            assertEquals(987654321L, endTime)
            assertEquals(5, number)
            assertEquals("foreground", appState)
            assertEquals(16090292309L, terminationTime)
            assertEquals(123456780L, lastHeartbeatTime)
            assertTrue(checkNotNull(isEndedCleanly))
            assertTrue(checkNotNull(isReceivedTermination))
            assertTrue(isColdStart)
            assertEquals(listOf("fake-event-id"), eventIds)
            assertEquals(listOf("fake-info-id"), infoLogIds)
            assertEquals(listOf("fake-warn-id"), warningLogIds)
            assertEquals(listOf("fake-err-id"), errorLogIds)
            assertEquals(listOf("fake-network-id"), networkLogIds)
            assertEquals("fake-crash-id", crashReportId)
            assertEquals(LifeEventType.STATE, endType)
            assertEquals(LifeEventType.STATE, startType)
            assertEquals(1223L, startupDuration)
            assertEquals(5000L, startupThreshold)
            assertEquals(109L, sdkStartupDuration)
            assertEquals(mapOf("fake-key" to "fake-value"), properties)
        }
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<Session>()
    }
}
