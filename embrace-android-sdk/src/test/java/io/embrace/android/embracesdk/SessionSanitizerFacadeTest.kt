package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakePerformanceInfo
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.gating.SessionSanitizerFacade
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.payload.DeviceInfo
import io.embrace.android.embracesdk.payload.Orientation
import io.embrace.android.embracesdk.payload.SessionMessage
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class SessionSanitizerFacadeTest {

    private val sessionPerformanceInfo = fakePerformanceInfo()

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

    private val sessionMessage = SessionMessage(
        session = session,
        deviceInfo = DeviceInfo(),
        performanceInfo = sessionPerformanceInfo,
        metadata = EnvelopeMetadata(
            email = "example@embrace.com",
            personas = setOf("personas")
        )
    )

    private val enabledComponents = setOf(
        SessionGatingKeys.BREADCRUMBS_TAPS,
        SessionGatingKeys.BREADCRUMBS_VIEWS,
        SessionGatingKeys.BREADCRUMBS_CUSTOM_VIEWS,
        SessionGatingKeys.BREADCRUMBS_WEB_VIEWS,
        SessionGatingKeys.BREADCRUMBS_CUSTOM,
        SessionGatingKeys.USER_PERSONAS,
        SessionGatingKeys.SESSION_PROPERTIES,
        SessionGatingKeys.SESSION_ORIENTATIONS,
        SessionGatingKeys.SESSION_USER_TERMINATION,
        SessionGatingKeys.SESSION_MOMENTS,
        SessionGatingKeys.LOGS_INFO,
        SessionGatingKeys.LOGS_WARN,
        SessionGatingKeys.STARTUP_MOMENT,
        SessionGatingKeys.PERFORMANCE_NETWORK,
        SessionGatingKeys.PERFORMANCE_ANR,
        SessionGatingKeys.PERFORMANCE_CURRENT_DISK_USAGE,
        SessionGatingKeys.PERFORMANCE_CPU,
        SessionGatingKeys.PERFORMANCE_CONNECTIVITY
    )

    @Test
    fun `test if it keeps all event message components`() {
        val sanitizedMessage =
            SessionSanitizerFacade(sessionMessage, enabledComponents).getSanitizedMessage()

        assertNotNull(sanitizedMessage.metadata?.personas)

        assertNotNull(sanitizedMessage.session.properties)
        assertNotNull(sanitizedMessage.session.orientations)
        assertNotNull(sanitizedMessage.session.terminationTime)
        assertNotNull(sanitizedMessage.session.isReceivedTermination)
        assertNotNull(sanitizedMessage.session.infoLogIds)
        assertNotNull(sanitizedMessage.session.infoLogsAttemptedToSend)
        assertNotNull(sanitizedMessage.session.warningLogIds)
        assertNotNull(sanitizedMessage.session.warnLogsAttemptedToSend)
        assertNotNull(sanitizedMessage.session.eventIds)
        assertNotNull(sanitizedMessage.session.startupDuration)
        assertNotNull(sanitizedMessage.session.startupThreshold)

        assertNotNull(sanitizedMessage.performanceInfo?.diskUsage)

        assertNotNull(sanitizedMessage.deviceInfo)
    }

    @Test
    fun `test if it sanitizes event message components`() {
        // uses an empty set for enabled components
        val sanitizedMessage =
            SessionSanitizerFacade(sessionMessage, setOf()).getSanitizedMessage()

        assertNull(sanitizedMessage.metadata?.personas)

        assertNull(sanitizedMessage.session.properties)
        assertNull(sanitizedMessage.session.orientations)
        assertNull(sanitizedMessage.session.terminationTime)
        assertNull(sanitizedMessage.session.isReceivedTermination)
        assertNull(sanitizedMessage.session.infoLogIds)
        assertNull(sanitizedMessage.session.infoLogsAttemptedToSend)
        assertNull(sanitizedMessage.session.warningLogIds)
        assertNull(sanitizedMessage.session.warnLogsAttemptedToSend)
        assertNull(sanitizedMessage.session.eventIds)
        assertNull(sanitizedMessage.session.startupDuration)
        assertNull(sanitizedMessage.session.startupThreshold)

        assertNull(sanitizedMessage.performanceInfo?.diskUsage)

        assertNotNull(sanitizedMessage.deviceInfo)
    }
}
