package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.gating.SessionSanitizerFacade
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.Breadcrumbs
import io.embrace.android.embracesdk.payload.DeviceInfo
import io.embrace.android.embracesdk.payload.Orientation
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.UserInfo
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test

internal class SessionSanitizerFacadeTest {

    private val breadcrumbs = Breadcrumbs(
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
    )

    private val sessionPerformanceInfo = PerformanceInfo(
        anrIntervals = mockk(relaxed = true),
        networkInterfaceIntervals = mockk(),
        memoryWarnings = mockk(),
        diskUsage = mockk(),
        networkRequests = mockk()
    )

    private val userInfo = UserInfo(
        personas = setOf("personas"),
        email = "example@embrace.com"
    )

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
        userInfo = userInfo,
        appInfo = AppInfo(),
        deviceInfo = DeviceInfo(),
        performanceInfo = sessionPerformanceInfo,
        breadcrumbs = breadcrumbs
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
        SessionGatingKeys.PERFORMANCE_CONNECTIVITY,
        SessionGatingKeys.PERFORMANCE_LOW_MEMORY
    )

    @Test
    fun `test if it keeps all event message components`() {
        val sanitizedMessage =
            SessionSanitizerFacade(sessionMessage, enabledComponents).getSanitizedMessage()

        val crumbs = checkNotNull(sanitizedMessage.breadcrumbs)
        Assert.assertNotNull(crumbs.customBreadcrumbs)
        Assert.assertNotNull(crumbs.viewBreadcrumbs)
        Assert.assertNotNull(crumbs.fragmentBreadcrumbs)
        Assert.assertNotNull(crumbs.tapBreadcrumbs)
        Assert.assertNotNull(crumbs.webViewBreadcrumbs)

        Assert.assertNotNull(sanitizedMessage.userInfo?.personas)

        Assert.assertNotNull(sanitizedMessage.session.properties)
        Assert.assertNotNull(sanitizedMessage.session.orientations)
        Assert.assertNotNull(sanitizedMessage.session.terminationTime)
        Assert.assertNotNull(sanitizedMessage.session.isReceivedTermination)
        Assert.assertNotNull(sanitizedMessage.session.infoLogIds)
        Assert.assertNotNull(sanitizedMessage.session.infoLogsAttemptedToSend)
        Assert.assertNotNull(sanitizedMessage.session.warningLogIds)
        Assert.assertNotNull(sanitizedMessage.session.warnLogsAttemptedToSend)
        Assert.assertNotNull(sanitizedMessage.session.eventIds)
        Assert.assertNotNull(sanitizedMessage.session.startupDuration)
        Assert.assertNotNull(sanitizedMessage.session.startupThreshold)

        Assert.assertNotNull(sanitizedMessage.performanceInfo?.networkRequests)
        Assert.assertNotNull(sanitizedMessage.performanceInfo?.anrIntervals)
        Assert.assertNotNull(sanitizedMessage.performanceInfo?.networkInterfaceIntervals)
        Assert.assertNotNull(sanitizedMessage.performanceInfo?.memoryWarnings)
        Assert.assertNotNull(sanitizedMessage.performanceInfo?.diskUsage)

        Assert.assertNotNull(sanitizedMessage.appInfo)
        Assert.assertNotNull(sanitizedMessage.deviceInfo)
    }

    @Test
    fun `test if it sanitizes event message components`() {
        // uses an empty set for enabled components
        val sanitizedMessage =
            SessionSanitizerFacade(sessionMessage, setOf()).getSanitizedMessage()

        val crumbs = checkNotNull(sanitizedMessage.breadcrumbs)
        Assert.assertNull(crumbs.customBreadcrumbs)
        Assert.assertNull(crumbs.viewBreadcrumbs)
        Assert.assertNull(crumbs.fragmentBreadcrumbs)
        Assert.assertNull(crumbs.tapBreadcrumbs)
        Assert.assertNull(crumbs.webViewBreadcrumbs)

        Assert.assertNull(sanitizedMessage.userInfo?.personas)

        Assert.assertNull(sanitizedMessage.session.properties)
        Assert.assertNull(sanitizedMessage.session.orientations)
        Assert.assertNull(sanitizedMessage.session.terminationTime)
        Assert.assertNull(sanitizedMessage.session.isReceivedTermination)
        Assert.assertNull(sanitizedMessage.session.infoLogIds)
        Assert.assertNull(sanitizedMessage.session.infoLogsAttemptedToSend)
        Assert.assertNull(sanitizedMessage.session.warningLogIds)
        Assert.assertNull(sanitizedMessage.session.warnLogsAttemptedToSend)
        Assert.assertNull(sanitizedMessage.session.eventIds)
        Assert.assertNull(sanitizedMessage.session.startupDuration)
        Assert.assertNull(sanitizedMessage.session.startupThreshold)

        Assert.assertNull(sanitizedMessage.performanceInfo?.networkRequests)
        Assert.assertNull(sanitizedMessage.performanceInfo?.anrIntervals)
        Assert.assertNull(sanitizedMessage.performanceInfo?.networkInterfaceIntervals)
        Assert.assertNull(sanitizedMessage.performanceInfo?.memoryWarnings)
        Assert.assertNull(sanitizedMessage.performanceInfo?.diskUsage)

        Assert.assertNotNull(sanitizedMessage.appInfo)
        Assert.assertNotNull(sanitizedMessage.deviceInfo)
    }
}
