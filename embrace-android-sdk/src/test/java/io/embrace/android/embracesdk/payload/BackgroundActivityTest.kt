package io.embrace.android.embracesdk.payload

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class BackgroundActivityTest {

    private val info = Session(
        sessionId = "fake-session-id",
        startTime = 123456789L,
        endTime = 987654321L,
        lastHeartbeatTime = 123456780L,
        eventIds = listOf("fake-event-id"),
        infoLogIds = listOf("fake-info-id"),
        warningLogIds = listOf("fake-warn-id"),
        errorLogIds = listOf("fake-err-id"),
        crashReportId = "fake-crash-id",
        endType = Session.LifeEventType.BKGND_STATE
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
            assertEquals(123456780L, lastHeartbeatTime)
            assertEquals(listOf("fake-event-id"), eventIds)
            assertEquals(listOf("fake-info-id"), infoLogIds)
            assertEquals(listOf("fake-warn-id"), warningLogIds)
            assertEquals(listOf("fake-err-id"), errorLogIds)
            assertEquals("fake-crash-id", crashReportId)
            assertEquals(Session.LifeEventType.BKGND_STATE, endType)
        }
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<Session>()
    }
}
