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
        data = session,
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

        Assert.assertNotNull(sanitizedMessage.data.properties)
        Assert.assertNotNull(sanitizedMessage.data.orientations)
        Assert.assertNotNull(sanitizedMessage.data.terminationTime)
        Assert.assertNotNull(sanitizedMessage.data.isReceivedTermination)
        Assert.assertNotNull(sanitizedMessage.data.infoLogIds)
        Assert.assertNotNull(sanitizedMessage.data.infoLogsAttemptedToSend)
        Assert.assertNotNull(sanitizedMessage.data.warningLogIds)
        Assert.assertNotNull(sanitizedMessage.data.warnLogsAttemptedToSend)
        Assert.assertNotNull(sanitizedMessage.data.eventIds)
        Assert.assertNotNull(sanitizedMessage.data.startupDuration)
        Assert.assertNotNull(sanitizedMessage.data.startupThreshold)

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

        Assert.assertNull(sanitizedMessage.data.properties)
        Assert.assertNull(sanitizedMessage.data.orientations)
        Assert.assertNull(sanitizedMessage.data.terminationTime)
        Assert.assertNull(sanitizedMessage.data.isReceivedTermination)
        Assert.assertNull(sanitizedMessage.data.infoLogIds)
        Assert.assertNull(sanitizedMessage.data.infoLogsAttemptedToSend)
        Assert.assertNull(sanitizedMessage.data.warningLogIds)
        Assert.assertNull(sanitizedMessage.data.warnLogsAttemptedToSend)
        Assert.assertNull(sanitizedMessage.data.eventIds)
        Assert.assertNull(sanitizedMessage.data.startupDuration)
        Assert.assertNull(sanitizedMessage.data.startupThreshold)

        Assert.assertNull(sanitizedMessage.performanceInfo?.networkRequests)
        Assert.assertNull(sanitizedMessage.performanceInfo?.anrIntervals)
        Assert.assertNull(sanitizedMessage.performanceInfo?.networkInterfaceIntervals)
        Assert.assertNull(sanitizedMessage.performanceInfo?.memoryWarnings)
        Assert.assertNull(sanitizedMessage.performanceInfo?.diskUsage)

        Assert.assertNotNull(sanitizedMessage.appInfo)
        Assert.assertNotNull(sanitizedMessage.deviceInfo)
    }
}
