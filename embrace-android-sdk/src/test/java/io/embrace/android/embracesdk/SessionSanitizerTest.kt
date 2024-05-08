package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.gating.SessionSanitizer
import io.embrace.android.embracesdk.payload.Orientation
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        assertNotNull(result.properties)
        assertNotNull(result.orientations)
        assertNotNull(result.terminationTime)
        assertNotNull(result.isReceivedTermination)
        assertNotNull(result.infoLogIds)
        assertNotNull(result.infoLogsAttemptedToSend)
        assertNotNull(result.warningLogIds)
        assertNotNull(result.warnLogsAttemptedToSend)
        assertNotNull(result.eventIds)
        assertNotNull(result.startupDuration)
        assertNotNull(result.startupThreshold)
    }

    @Test
    fun `test if it sanitizes session info`() {
        val components = setOf<String>()

        val result = SessionSanitizer(session, components).sanitize()

        assertNull(result.properties)
        assertNull(result.orientations)
        assertNull(result.terminationTime)
        assertNull(result.isReceivedTermination)
        assertNull(result.infoLogIds)
        assertNull(result.infoLogsAttemptedToSend)
        assertNull(result.warningLogIds)
        assertNull(result.warnLogsAttemptedToSend)
        assertNull(result.eventIds)
        assertNull(result.startupDuration)
        assertNull(result.startupThreshold)
    }
}
