package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.gating.SessionSanitizer
import io.embrace.android.embracesdk.payload.Orientation
import org.junit.Assert
import org.junit.Test

internal class SessionSanitizerTest {

    private val session = fakeSession().copy(
        properties = mapOf("example" to "example"),
        orientations = listOf(Orientation(0, 0L)),
        terminationTime = 100L,
        isReceivedTermination = false,
        infoLogIds = listOf("infoLog"),
        infoLogsAttemptedToSend = 1,
        warningLogIds = listOf("warningLog"),
        warnLogsAttemptedToSend = 1,
        eventIds = listOf("eventId"),
        startupDuration = 100L,
        startupThreshold = 500L
    )

    @Test
    fun `test if it keeps all session info`() {
        // enabled components contains everything about session
        val components = setOf(
            SessionGatingKeys.SESSION_PROPERTIES,
            SessionGatingKeys.SESSION_ORIENTATIONS,
            SessionGatingKeys.SESSION_USER_TERMINATION,
            SessionGatingKeys.SESSION_MOMENTS,
            SessionGatingKeys.LOGS_INFO,
            SessionGatingKeys.LOGS_WARN,
            SessionGatingKeys.STARTUP_MOMENT
        )

        val result = SessionSanitizer(session, components).sanitize()

        Assert.assertNotNull(result.properties)
        Assert.assertNotNull(result.orientations)
        Assert.assertNotNull(result.terminationTime)
        Assert.assertNotNull(result.isReceivedTermination)
        Assert.assertNotNull(result.infoLogIds)
        Assert.assertNotNull(result.infoLogsAttemptedToSend)
        Assert.assertNotNull(result.warningLogIds)
        Assert.assertNotNull(result.warnLogsAttemptedToSend)
        Assert.assertNotNull(result.eventIds)
        Assert.assertNotNull(result.startupDuration)
        Assert.assertNotNull(result.startupThreshold)
        Assert.assertNull(result.betaFeatures)
    }

    @Test
    fun `test if it sanitizes session info`() {
        val components = setOf<String>()

        val result = SessionSanitizer(session, components).sanitize()

        Assert.assertNull(result.properties)
        Assert.assertNull(result.orientations)
        Assert.assertNull(result.terminationTime)
        Assert.assertNull(result.isReceivedTermination)
        Assert.assertNull(result.infoLogIds)
        Assert.assertNull(result.infoLogsAttemptedToSend)
        Assert.assertNull(result.warningLogIds)
        Assert.assertNull(result.warnLogsAttemptedToSend)
        Assert.assertNull(result.eventIds)
        Assert.assertNull(result.startupDuration)
        Assert.assertNull(result.startupThreshold)
        Assert.assertNull(result.betaFeatures)
    }
}
