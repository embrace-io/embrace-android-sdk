package io.embrace.android.embracesdk.event

import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.EventDescription
import io.embrace.android.embracesdk.internal.MessageType
import io.embrace.android.embracesdk.internal.StartupEventInfo
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.UserInfo
import io.embrace.android.embracesdk.session.EmbraceSessionProperties
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

internal class EventHandlerTest {

    private lateinit var eventHandler: EventHandler
    private lateinit var clock: FakeClock
    lateinit var cfg: RemoteConfig

    companion object {
        private lateinit var mockDeliveryService: DeliveryService
        private lateinit var mockConfigService: ConfigService
        private lateinit var mockPerformanceService: PerformanceInfoService
        private lateinit var mockUserService: UserService
        private lateinit var gatingService: GatingService
        private lateinit var mockSessionProperties: EmbraceSessionProperties
        private lateinit var logger: InternalEmbraceLogger
        private lateinit var mockStartup: StartupEventInfo
        private lateinit var mockLateTimer: ScheduledFuture<*>
        private lateinit var mockUserInfo: UserInfo
        private lateinit var fakeMetadataService: FakeAndroidMetadataService
        private lateinit var blockingScheduledExecutorService: BlockingScheduledExecutorService
        private lateinit var scheduledExecutorService: ScheduledExecutorService

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockDeliveryService = mockk(relaxed = true)
            mockConfigService = mockk(relaxed = true)
            mockPerformanceService = mockk()
            mockUserService = mockk()
            gatingService = FakeGatingService()
            mockSessionProperties = mockk()
            logger = InternalEmbraceLogger()
            mockStartup = mockk(relaxed = true)
            mockLateTimer = mockk(relaxed = true)
            mockUserInfo = mockk()
            mockkStatic(StartupEventInfo::class)
            mockkStatic(Event::class)
            mockkStatic(EventMessage::class)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    @Before
    fun before() {
        cfg = RemoteConfig()
        every { mockConfigService.sessionBehavior } returns fakeSessionBehavior { cfg }
        every { mockConfigService.dataCaptureEventBehavior } returns fakeDataCaptureEventBehavior { cfg }

        clock = FakeClock()
        blockingScheduledExecutorService = BlockingScheduledExecutorService()
        scheduledExecutorService = blockingScheduledExecutorService
        fakeMetadataService = FakeAndroidMetadataService(sessionId = "session-id")
        eventHandler = EventHandler(
            fakeMetadataService,
            mockConfigService,
            mockUserService,
            mockPerformanceService,
            mockDeliveryService,
            logger,
            clock,
            scheduledExecutorService
        )
    }

    @After
    fun after() {
        clearAllMocks(answers = false)
    }

    @Test
    fun `if event name is empty then event should not be allowed to start`() {
        val allowed = eventHandler.isAllowedToStart("")
        assertFalse(allowed)
    }

    @Test
    fun `if event name is disabled then event should not be allowed to start`() {
        val disabledEvent = "disabled-event"
        every { mockConfigService.dataCaptureEventBehavior.isEventEnabled(disabledEvent) } returns false
        val allowed = eventHandler.isAllowedToStart(disabledEvent)

        assertFalse(allowed)
    }

    @Test
    fun `if event type is disabled then event should not be allowed to start`() {
        val event = "event"
        every { mockConfigService.dataCaptureEventBehavior.isEventEnabled(event) } returns true
        every { mockConfigService.dataCaptureEventBehavior.isMessageTypeEnabled(MessageType.EVENT) } returns false
        val allowed = eventHandler.isAllowedToStart(event)

        assertFalse(allowed)
    }

    @Test
    fun `if worker is shut down, then event should not be allowed to start`() {
        val event = "event"
        every { mockConfigService.dataCaptureEventBehavior.isEventEnabled(event) } returns true
        every { mockConfigService.dataCaptureEventBehavior.isMessageTypeEnabled(MessageType.EVENT) } returns true
        scheduledExecutorService.shutdown()
        val allowed = eventHandler.isAllowedToStart(event)

        assertFalse(allowed)
    }

    @Test
    fun `if none of the above, event should be allowed to start`() {
        val event = "event"
        every { mockConfigService.dataCaptureEventBehavior.isEventEnabled(event) } returns true
        every { mockConfigService.dataCaptureEventBehavior.isMessageTypeEnabled(MessageType.EVENT) } returns true
        val allowed = eventHandler.isAllowedToStart(event)

        assertTrue(allowed)
    }

    @Test
    fun `if event type is disabled then event should not be allowed to end`() {
        every { mockConfigService.dataCaptureEventBehavior.isMessageTypeEnabled(MessageType.EVENT) } returns false
        val allowed = eventHandler.isAllowedToEnd()

        assertFalse(allowed)
    }

    @Test
    fun `verify event is allowed to end`() {
        every { mockConfigService.dataCaptureEventBehavior.isMessageTypeEnabled(MessageType.EVENT) } returns true
        val allowed = eventHandler.isAllowedToEnd()

        assertTrue(allowed)
    }

    @Test
    fun `verify build startup event successfully`() {
        val duration = 456L
        val threshold = 123L
        val startEvent = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 100L,
            type = EmbraceEvent.Type.START,
            lateThreshold = threshold
        )
        val endEvent = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 200L,
            type = EmbraceEvent.Type.END,
            duration = duration
        )

        val event = eventHandler.buildStartupEventInfo(startEvent, endEvent)

        assertEquals(event.duration, duration)
        assertEquals(event.threshold, threshold)
    }

    @Test
    fun `verify onEventEnded builds event and sends the event for a non late event`() {
        val eventProperties = mapOf<String, Any>()
        val startTime = 100L
        val endTime = 300L
        val sessionPropertiesMap: Map<String, String> = mapOf()
        val customPropertiesMap: Map<String, String> = mapOf()
        clock.setCurrentTime(endTime)
        val originEventId = "origin-event-id"
        val originEventName = "origin-event-name"

        val originEvent = Event(
            timestamp = startTime,
            eventId = originEventId,
            name = originEventName,
            type = EmbraceEvent.Type.START
        )
        val originEventDescription = EventDescription(mockLateTimer, originEvent)

        val builtEndEvent = Event(
            eventId = originEventId,
            type = EmbraceEvent.Type.END,
            appState = fakeMetadataService.getAppState(),
            name = originEventName,
            timestamp = endTime,
            customProperties = customPropertiesMap,
            sessionProperties = sessionPropertiesMap,
            sessionId = fakeMetadataService.activeSessionId,
            duration = endTime - startTime
        )

        val mockPerformanceInfo: PerformanceInfo = mockk()

        every { mockSessionProperties.get() } returns sessionPropertiesMap
        every {
            mockPerformanceService.getPerformanceInfo(
                startTime,
                endTime,
                false
            )
        } returns mockPerformanceInfo
        every { mockUserService.getUserInfo() } returns mockUserInfo

        val builtEndEventMessage = EventMessage(
            event = builtEndEvent,
            userInfo = mockUserInfo,
            performanceInfo = mockPerformanceInfo
        )

        val result = eventHandler.onEventEnded(
            originEventDescription,
            false,
            eventProperties,
            mockSessionProperties
        )

        verify { mockLateTimer.cancel(false) }
        verify { mockDeliveryService.sendEventAsync(any()) }
        assertEquals(builtEndEventMessage, result)
    }
    @Test
    fun `verify onEventEnded builds event and sends the event`() {
        val eventProperties = mapOf<String, Any>()
        val startTime = 100L
        val endTime = 300L
        val sessionPropertiesMap: Map<String, String> = mapOf()
        val customPropertiesMap: Map<String, String> = mapOf()
        clock.setCurrentTime(endTime)
        val originEventId = "origin-event-id"
        val originEventName = "origin-event-name"
        val mockPerformanceInfo: PerformanceInfo = mockk()

        val originEvent = Event(
            timestamp = startTime,
            eventId = originEventId,
            name = originEventName,
            type = EmbraceEvent.Type.START
        )
        val originEventDescription = EventDescription(mockLateTimer, originEvent)

        every { mockSessionProperties.get() } returns sessionPropertiesMap
        every {
            mockPerformanceService.getPerformanceInfo(
                startTime,
                endTime,
                false
            )
        } returns mockPerformanceInfo
        every { mockUserService.getUserInfo() } returns mockUserInfo
        val endEvent = Event(
            eventId = originEventId,
            type = EmbraceEvent.Type.LATE,
            appState = fakeMetadataService.getAppState(),
            name = originEventName,
            timestamp = endTime,
            customProperties = customPropertiesMap,
            sessionProperties = sessionPropertiesMap,
            sessionId = fakeMetadataService.activeSessionId,
            duration = endTime - startTime
        )
        val builtEndEventMessage = EventMessage(
            event = endEvent,
            userInfo = mockUserInfo,
            performanceInfo = mockPerformanceInfo
        )
        cfg = createGatingConfig(setOf("s_mts"))

        val result = eventHandler.onEventEnded(
            originEventDescription,
            true,
            eventProperties,
            mockSessionProperties
        )

        verify { mockLateTimer.cancel(false) }
        verify { mockDeliveryService.sendEventAsync(any()) }
        assertEquals(builtEndEventMessage, result)
    }

    @Test
    fun `verify onEventStarted builds event, sends event and sets the timer`() {
        val eventId = "event-id"
        val eventName = "event-name"
        val startTime = 123L
        val threshold = 100L
        val sessionPropertiesMap: Map<String, String> = mapOf()
        val customProperties: Map<String, String> = mapOf()
        val builtEvent = Event(
            eventId = eventId,
            type = EmbraceEvent.Type.START,
            appState = fakeMetadataService.getAppState(),
            name = eventName,
            lateThreshold = threshold,
            timestamp = startTime,
            sessionProperties = sessionPropertiesMap,
            customProperties = customProperties,
            sessionId = fakeMetadataService.activeSessionId
        )

        clock.setCurrentTime(456)

        every { mockSessionProperties.get() } returns sessionPropertiesMap
        every { mockUserService.getUserInfo() } returns mockUserInfo

        val builtEventMessage = EventMessage(
            event = builtEvent,
            appInfo = fakeMetadataService.getAppInfo(),
            userInfo = mockUserInfo,
            deviceInfo = fakeMetadataService.getDeviceInfo()
        )
        cfg = RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                sessionComponents = setOf("s_mts")
            ),
            eventLimits = mapOf(eventId to threshold)
        )

        var hasCallableBeenInvoked = false
        val eventDescription = eventHandler.onEventStarted(
            eventId,
            eventName,
            startTime,
            mockSessionProperties,
            mapOf()
        ) {
            hasCallableBeenInvoked = true
        }
        assertNotNull(eventDescription)
        assertEquals(builtEvent, eventDescription.event)
        verify { mockDeliveryService.sendEventAsync(builtEventMessage) }
        blockingScheduledExecutorService.runCurrentlyBlocked()
        assertTrue(hasCallableBeenInvoked)
    }

    @Test
    fun `verify onEventStarted prevents send the startup event if feature gating gates it`() {
        val eventId = "event-id"
        val eventName = EmbraceEventService.STARTUP_EVENT_NAME
        val startTime = 123L
        val threshold = 100L
        val sessionPropertiesMap: Map<String, String> = mapOf()
        val mockTimeoutCallback: Runnable = mockk()
        clock.setCurrentTime(456)

        every { mockSessionProperties.get() } returns sessionPropertiesMap
        every { mockUserService.getUserInfo() } returns mockUserInfo

        cfg = RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                sessionComponents = emptySet()
            ),
            eventLimits = mapOf(eventId to threshold)
        )

        eventHandler.onEventStarted(
            eventId,
            eventName,
            startTime,
            mockSessionProperties,
            mapOf(),
            mockTimeoutCallback
        )

        verify { mockDeliveryService wasNot Called }
    }

    @Test
    fun `verify onEventStarted sends the startup event if feature gating allows it`() {
        val eventId = "event-id"
        val eventName = EmbraceEventService.STARTUP_EVENT_NAME
        val startTime = 123L
        val threshold = 100L
        val sessionPropertiesMap: Map<String, String> = mapOf()
        val mockTimeoutCallback: Runnable = mockk()
        clock.setCurrentTime(456)

        every { mockSessionProperties.get() } returns sessionPropertiesMap
        every { mockUserService.getUserInfo() } returns mockUserInfo

        cfg = RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                sessionComponents = setOf("mts_st")
            ),
            eventLimits = mapOf(eventId to threshold)
        )

        eventHandler.onEventStarted(
            eventId,
            eventName,
            startTime,
            mockSessionProperties,
            mapOf(),
            mockTimeoutCallback
        )

        verify { mockDeliveryService.sendEventAsync(any()) }
    }

    @Test
    fun `verify onEventStarted prevents send the event if feature gating gates it`() {
        val eventId = "event-id"
        val eventName = "event-name"
        val startTime = 123L
        val threshold = 100L
        val sessionPropertiesMap: Map<String, String> = mapOf()
        val mockTimeoutCallback: Runnable = mockk()
        clock.setCurrentTime(456)

        every { mockSessionProperties.get() } returns sessionPropertiesMap
        every { mockUserService.getUserInfo() } returns mockUserInfo

        cfg = RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                sessionComponents = emptySet()
            ),
            eventLimits = mapOf(eventId to threshold)
        )

        eventHandler.onEventStarted(
            eventId,
            eventName,
            startTime,
            mockSessionProperties,
            mapOf(),
            mockTimeoutCallback
        )

        verify { mockDeliveryService wasNot Called }
    }

    @Test
    fun `verify onEventStarted sends the event if feature gating allows it`() {
        val eventId = "event-id"
        val eventName = "event-name"
        val startTime = 123L
        val threshold = 100L
        val sessionPropertiesMap: Map<String, String> = mapOf()
        val mockTimeoutCallback: Runnable = mockk()
        clock.setCurrentTime(456)

        every { mockSessionProperties.get() } returns sessionPropertiesMap
        every { mockUserService.getUserInfo() } returns mockUserInfo

        cfg = RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                sessionComponents = setOf("s_mts")
            ),
            eventLimits = mapOf(eventId to threshold)
        )

        eventHandler.onEventStarted(
            eventId,
            eventName,
            startTime,
            mockSessionProperties,
            mapOf(),
            mockTimeoutCallback
        )

        verify { mockDeliveryService.sendEventAsync(any()) }
    }

    @Test
    fun `verify onEventEnded prevents send event if feature gating gates it`() {
        val eventProperties = mapOf<String, Any>()
        val startTime = 100L
        val endTime = 300L
        val sessionPropertiesMap: Map<String, String> = mapOf()
        clock.setCurrentTime(endTime)
        val originEventId = "origin-event-id"
        val originEventName = "origin-event-name"
        val mockPerformanceInfo: PerformanceInfo = mockk()

        val originEvent = Event(
            timestamp = startTime,
            eventId = originEventId,
            name = originEventName,
            type = EmbraceEvent.Type.START
        )
        val originEventDescription = EventDescription(mockLateTimer, originEvent)
        every { mockSessionProperties.get() } returns sessionPropertiesMap
        every {
            mockPerformanceService.getPerformanceInfo(
                startTime,
                endTime,
                false
            )
        } returns mockPerformanceInfo
        every { mockUserService.getUserInfo() } returns mockUserInfo
        cfg = createGatingConfig(emptySet())

        eventHandler.onEventEnded(
            originEventDescription,
            false,
            eventProperties,
            mockSessionProperties
        )

        verify { mockDeliveryService wasNot Called }
    }

    @Test
    fun `verify onEventEnded sends event if feature gating allows it`() {
        val eventProperties = mapOf<String, Any>()
        val startTime = 100L
        val endTime = 300L
        val sessionPropertiesMap: Map<String, String> = mapOf()
        clock.setCurrentTime(endTime)
        val originEventId = "origin-event-id"
        val originEventName = "origin-event-name"
        val mockPerformanceInfo: PerformanceInfo = mockk()

        val originEvent = Event(
            timestamp = startTime,
            eventId = originEventId,
            name = originEventName,
            type = EmbraceEvent.Type.START
        )
        val originEventDescription = EventDescription(mockLateTimer, originEvent)

        every { mockSessionProperties.get() } returns sessionPropertiesMap
        every {
            mockPerformanceService.getPerformanceInfo(
                startTime,
                endTime,
                false
            )
        } returns mockPerformanceInfo
        every { mockUserService.getUserInfo() } returns mockUserInfo
        cfg = createGatingConfig(setOf("s_mts"))

        eventHandler.onEventEnded(
            originEventDescription,
            false,
            eventProperties,
            mockSessionProperties
        )

        verify { mockDeliveryService.sendEventAsync(any()) }
    }

    @Test
    fun `verify onEventEnded prevents send startup event if feature gating gates it`() {
        val eventProperties = mapOf<String, Any>()
        val startTime = 100L
        val endTime = 300L
        val sessionPropertiesMap: Map<String, String> = mapOf()
        clock.setCurrentTime(endTime)
        val originEventId = "origin-event-id"
        val originEventName = EmbraceEventService.STARTUP_EVENT_NAME
        val mockPerformanceInfo: PerformanceInfo = mockk()

        val originEvent = Event(
            timestamp = startTime,
            eventId = originEventId,
            name = originEventName,
            type = EmbraceEvent.Type.START
        )
        val originEventDescription = EventDescription(mockLateTimer, originEvent)

        every { mockSessionProperties.get() } returns sessionPropertiesMap
        every {
            mockPerformanceService.getPerformanceInfo(
                startTime,
                endTime,
                false
            )
        } returns mockPerformanceInfo
        every { mockUserService.getUserInfo() } returns mockUserInfo
        cfg = createGatingConfig(emptySet())

        eventHandler.onEventEnded(
            originEventDescription,
            false,
            eventProperties,
            mockSessionProperties
        )

        verify { mockDeliveryService wasNot Called }
    }

    @Test
    fun `verify onEventEnded sends startup event if feature gating allows it`() {
        val eventProperties = mapOf<String, Any>()
        val startTime = 100L
        val endTime = 300L
        val sessionPropertiesMap: Map<String, String> = mapOf()
        clock.setCurrentTime(endTime)
        val originEventId = "origin-event-id"
        val originEventName = EmbraceEventService.STARTUP_EVENT_NAME

        val mockPerformanceInfo: PerformanceInfo = mockk()

        val originEvent = Event(
            timestamp = startTime,
            eventId = originEventId,
            name = originEventName,
            type = EmbraceEvent.Type.START
        )
        val originEventDescription = EventDescription(mockLateTimer, originEvent)

        every { mockSessionProperties.get() } returns sessionPropertiesMap
        every {
            mockPerformanceService.getPerformanceInfo(
                startTime,
                endTime,
                false
            )
        } returns mockPerformanceInfo
        every { mockUserService.getUserInfo() } returns mockUserInfo
        createGatingConfig(setOf("s_mts"))

        eventHandler.onEventEnded(
            originEventDescription,
            false,
            eventProperties,
            mockSessionProperties
        )

        verify { mockDeliveryService.sendEventAsync(any()) }
    }

    private fun createGatingConfig(components: Set<String>) = RemoteConfig(
        sessionConfig = SessionRemoteConfig(
            sessionComponents = components
        )
    )
}
