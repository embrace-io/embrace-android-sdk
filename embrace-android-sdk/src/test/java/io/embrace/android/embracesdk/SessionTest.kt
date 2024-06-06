package io.embrace.android.embracesdk

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class SessionTest {

    private val info = Session(
        sessionId = "fake-session-id",
        startTime = 123456789L,
        endTime = 987654321L,
        lastHeartbeatTime = 123456780L,
        terminationTime = 16090292309L,
        eventIds = listOf("fake-event-id"),
        infoLogIds = listOf("fake-info-id"),
        warningLogIds = listOf("fake-warn-id"),
        errorLogIds = listOf("fake-err-id"),
        networkLogIds = listOf("fake-network-id"),
        crashReportId = "fake-crash-id",
        endType = LifeEventType.STATE,
        startupDuration = 1223,
        startupThreshold = 5000,
        sdkStartupDuration = 109
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
            assertEquals(16090292309L, terminationTime)
            assertEquals(123456780L, lastHeartbeatTime)
            assertEquals(listOf("fake-event-id"), eventIds)
            assertEquals(listOf("fake-info-id"), infoLogIds)
            assertEquals(listOf("fake-warn-id"), warningLogIds)
            assertEquals(listOf("fake-err-id"), errorLogIds)
            assertEquals(listOf("fake-network-id"), networkLogIds)
            assertEquals("fake-crash-id", crashReportId)
            assertEquals(LifeEventType.STATE, endType)
            assertEquals(1223L, startupDuration)
            assertEquals(5000L, startupThreshold)
            assertEquals(109L, sdkStartupDuration)
        }
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<Session>()
    }
}
