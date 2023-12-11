package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.BetaFeatures
import io.embrace.android.embracesdk.payload.ExceptionError
import io.embrace.android.embracesdk.payload.Orientation
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.SessionLifeEventType
import io.embrace.android.embracesdk.payload.UserInfo
import io.embrace.android.embracesdk.payload.WebViewInfo
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
        messageType = "fake-message-type",
        terminationTime = 16090292309L,
        eventIds = listOf("fake-event-id"),
        infoLogIds = listOf("fake-info-id"),
        warningLogIds = listOf("fake-warn-id"),
        errorLogIds = listOf("fake-err-id"),
        networkLogIds = listOf("fake-network-id"),
        infoLogsAttemptedToSend = 1,
        warnLogsAttemptedToSend = 2,
        errorLogsAttemptedToSend = 3,
        crashReportId = "fake-crash-id",
        endType = SessionLifeEventType.STATE,
        startType = SessionLifeEventType.STATE,
        startupDuration = 1223,
        startupThreshold = 5000,
        sdkStartupDuration = 109,
        unhandledExceptions = 1,
        user = UserInfo("fake-user-id", "fake-user-name"),
        exceptionError = ExceptionError(false),
        orientations = listOf(Orientation(1, 16092342200)),
        properties = mapOf("fake-key" to "fake-value"),
        symbols = mapOf("fake-native-key" to "fake-native-value"),
        betaFeatures = BetaFeatures(),
        webViewInfo = listOf(
            WebViewInfo(
                "fake-webview-id",
                url = "fake-url",
                startTime = 16090292309L
            )
        )
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
            assertEquals("fake-message-type", messageType)
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
            assertEquals(1, infoLogsAttemptedToSend)
            assertEquals(2, warnLogsAttemptedToSend)
            assertEquals(3, errorLogsAttemptedToSend)
            assertEquals("fake-crash-id", crashReportId)
            assertEquals(SessionLifeEventType.STATE, endType)
            assertEquals(SessionLifeEventType.STATE, startType)
            assertEquals(1223L, startupDuration)
            assertEquals(5000L, startupThreshold)
            assertEquals(109L, sdkStartupDuration)
            assertEquals(1, unhandledExceptions)
            assertEquals(ExceptionError(false), exceptionError)
            assertEquals(listOf(Orientation(1, 16092342200)), orientations)
            assertEquals(mapOf("fake-key" to "fake-value"), properties)
            assertEquals(mapOf("fake-native-key" to "fake-native-value"), symbols)
            assertEquals(BetaFeatures(), betaFeatures)
            assertEquals(1, webViewInfo?.size)
        }
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<Session>()
        assertNotNull(obj)
    }
}
