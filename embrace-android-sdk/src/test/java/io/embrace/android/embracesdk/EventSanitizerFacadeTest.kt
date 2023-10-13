package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.gating.EventSanitizerFacade
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.UserInfo
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test

internal class EventSanitizerFacadeTest {

    private val event = Event(
        eventId = "123",
        timestamp = 100L,
        duration = 1000L,
        appState = "state",
        type = EmbraceEvent.Type.INFO_LOG,
        customProperties = mapOf("custom" to 123),
        sessionProperties = mapOf("custom" to "custom"),
        logExceptionType = LogExceptionType.NONE.value
    )

    private val userInfo = UserInfo(
        personas = setOf("personas"),
        email = "example@embrace.com"
    )

    private val performanceInfo = PerformanceInfo(
        anrIntervals = mockk(relaxed = true),
        networkInterfaceIntervals = mockk(),
        memoryWarnings = mockk(),
        diskUsage = mockk()
    )

    private val eventMessage = EventMessage(
        event = event,
        userInfo = userInfo,
        appInfo = mockk(),
        deviceInfo = mockk(),
        performanceInfo = performanceInfo
    )

    private val enabledComponents = setOf(
        SessionGatingKeys.SESSION_PROPERTIES,
        SessionGatingKeys.LOG_PROPERTIES,
        SessionGatingKeys.USER_PERSONAS,
        SessionGatingKeys.PERFORMANCE_ANR,
        SessionGatingKeys.PERFORMANCE_CURRENT_DISK_USAGE,
        SessionGatingKeys.PERFORMANCE_CPU,
        SessionGatingKeys.PERFORMANCE_CONNECTIVITY,
        SessionGatingKeys.PERFORMANCE_LOW_MEMORY
    )

    @Test
    fun `test if it keeps all event message components`() {
        val sanitizedMessage =
            EventSanitizerFacade(eventMessage, enabledComponents).getSanitizedMessage()

        Assert.assertNotNull(sanitizedMessage.event.customPropertiesMap)
        Assert.assertNotNull(sanitizedMessage.event.sessionPropertiesMap)
        Assert.assertNotNull(sanitizedMessage.userInfo!!.personas)
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
        val eventSanitizer = EventSanitizerFacade(eventMessage, setOf())
        val sanitizedMessage = eventSanitizer.getSanitizedMessage()

        Assert.assertNull(sanitizedMessage.event.customPropertiesMap)
        Assert.assertNull(sanitizedMessage.event.sessionPropertiesMap)
        Assert.assertNull(sanitizedMessage.userInfo!!.personas)
        Assert.assertNull(sanitizedMessage.performanceInfo?.anrIntervals)
        Assert.assertNull(sanitizedMessage.performanceInfo?.networkInterfaceIntervals)
        Assert.assertNull(sanitizedMessage.performanceInfo?.memoryWarnings)
        Assert.assertNull(sanitizedMessage.performanceInfo?.diskUsage)

        Assert.assertNotNull(sanitizedMessage.appInfo)
        Assert.assertNotNull(sanitizedMessage.deviceInfo)
    }
}
