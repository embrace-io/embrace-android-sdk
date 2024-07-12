package io.embrace.android.embracesdk.event

import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.EventDescription
import io.embrace.android.embracesdk.internal.StartupEventInfo
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.UserInfo
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
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
    private lateinit var cfg: RemoteConfig

    companion object {
        private lateinit var deliveryService: FakeDeliveryService
        private lateinit var configService: ConfigService
        private lateinit var userService: UserService
        private lateinit var gatingService: GatingService
        private lateinit var sessionProperties: EmbraceSessionProperties
        private lateinit var logger: EmbLogger
        private lateinit var mockStartup: StartupEventInfo
        private lateinit var mockLateTimer: ScheduledFuture<*>
        private lateinit var userInfo: UserInfo
        private lateinit var fakeMetadataService: FakeMetadataService
        private lateinit var sessionIdTracker: FakeSessionIdTracker
        private lateinit var blockingScheduledExecutorService: BlockingScheduledExecutorService
        private lateinit var scheduledExecutorService: ScheduledExecutorService

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            userService = FakeUserService()
            gatingService = FakeGatingService()
            logger = EmbLoggerImpl()
            mockStartup = StartupEventInfo()
            mockLateTimer = mockk(relaxed = true)
            userInfo = UserInfo()
        }
    }

    @Before
    fun before() {
        deliveryService = FakeDeliveryService()

        cfg = RemoteConfig()
        configService = FakeConfigService(
            sessionBehavior = fakeSessionBehavior { cfg },
            dataCaptureEventBehavior = fakeDataCaptureEventBehavior { cfg }
        )
        sessionProperties = EmbraceSessionProperties(FakePreferenceService(), configService, logger)

        clock = FakeClock()
        blockingScheduledExecutorService = BlockingScheduledExecutorService()
        scheduledExecutorService = blockingScheduledExecutorService
        fakeMetadataService = FakeMetadataService(sessionId = "session-id")
        sessionIdTracker = FakeSessionIdTracker()
        eventHandler = EventHandler(
            fakeMetadataService,
            sessionIdTracker,
            configService,
            userService,
            deliveryService,
            logger,
            clock,
            ScheduledWorker(scheduledExecutorService)
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
        cfg = cfg.copy(disabledEventAndLogPatterns = setOf(disabledEvent))
        val allowed = eventHandler.isAllowedToStart(disabledEvent)

        assertFalse(allowed)
    }

    @Test
    fun `if worker is shut down, then event should be allowed to start`() {
        val event = "event"
        scheduledExecutorService.shutdown()
        val allowed = eventHandler.isAllowedToStart(event)
        assertTrue(allowed)
    }

    @Test
    fun `if none of the above, event should be allowed to start`() {
        val event = "event"
        val allowed = eventHandler.isAllowedToStart(event)

        assertTrue(allowed)
    }

    @Test
    fun `verify build startup event successfully`() {
        val duration = 456L
        val threshold = 123L
        val startEvent = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 100L,
            type = EventType.START,
            lateThreshold = threshold
        )
        val endEvent = Event(
            eventId = Uuid.getEmbUuid(),
            timestamp = 200L,
            type = EventType.END,
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
            type = EventType.START
        )
        val originEventDescription = EventDescription(mockLateTimer, originEvent)

        val builtEndEvent = Event(
            eventId = originEventId,
            type = EventType.END,
            appState = fakeMetadataService.getAppState(),
            name = originEventName,
            timestamp = endTime,
            customProperties = customPropertiesMap,
            sessionProperties = sessionPropertiesMap,
            sessionId = sessionIdTracker.getActiveSessionId(),
            duration = endTime - startTime
        )

        val builtEndEventMessage = EventMessage(
            event = builtEndEvent,
            userInfo = userInfo
        )

        val result = eventHandler.onEventEnded(
            originEventDescription,
            false,
            eventProperties,
            sessionProperties
        )

        verify { mockLateTimer.cancel(false) }
        assertEquals(result, deliveryService.sentMoments.last())
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

        val originEvent = Event(
            timestamp = startTime,
            eventId = originEventId,
            name = originEventName,
            type = EventType.START
        )
        val originEventDescription = EventDescription(mockLateTimer, originEvent)

        val endEvent = Event(
            eventId = originEventId,
            type = EventType.LATE,
            appState = fakeMetadataService.getAppState(),
            name = originEventName,
            timestamp = endTime,
            customProperties = customPropertiesMap,
            sessionProperties = sessionPropertiesMap,
            sessionId = sessionIdTracker.getActiveSessionId(),
            duration = endTime - startTime
        )
        val builtEndEventMessage = EventMessage(
            event = endEvent,
            userInfo = userInfo
        )
        cfg = createGatingConfig(setOf("s_mts"))

        val result = eventHandler.onEventEnded(
            originEventDescription,
            true,
            eventProperties,
            sessionProperties
        )

        verify { mockLateTimer.cancel(false) }
        assertEquals(result, deliveryService.sentMoments.last())
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
            type = EventType.START,
            appState = fakeMetadataService.getAppState(),
            name = eventName,
            lateThreshold = threshold,
            timestamp = startTime,
            sessionProperties = sessionPropertiesMap,
            customProperties = customProperties,
            sessionId = sessionIdTracker.getActiveSessionId()
        )

        clock.setCurrentTime(456)

        val builtEventMessage = EventMessage(
            event = builtEvent,
            appInfo = fakeMetadataService.getAppInfo(),
            userInfo = userInfo,
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
            sessionProperties,
            mapOf()
        ) {
            hasCallableBeenInvoked = true
        }
        assertNotNull(eventDescription)
        assertEquals(builtEvent, eventDescription.event)
        assertEquals(builtEventMessage, deliveryService.sentMoments.last())
        blockingScheduledExecutorService.runCurrentlyBlocked()
        assertTrue(hasCallableBeenInvoked)
    }

    @Test
    fun `verify onEventStarted prevents send the startup event if feature gating gates it`() {
        val eventId = "event-id"
        val eventName = EmbraceEventService.STARTUP_EVENT_NAME
        val startTime = 123L
        val threshold = 100L
        val mockTimeoutCallback = Runnable {}
        clock.setCurrentTime(456)

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
            sessionProperties,
            mapOf(),
            mockTimeoutCallback
        )
        assertTrue(deliveryService.sentMoments.isEmpty())
    }

    @Test
    fun `verify onEventStarted sends the startup event if feature gating allows it`() {
        val eventId = "event-id"
        val eventName = EmbraceEventService.STARTUP_EVENT_NAME
        val startTime = 123L
        val threshold = 100L
        val mockTimeoutCallback = Runnable {}
        clock.setCurrentTime(456)

        cfg = RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                sessionComponents = setOf("mts_st")
            ),
            eventLimits = mapOf(eventId to threshold)
        )

        val result = eventHandler.onEventStarted(
            eventId,
            eventName,
            startTime,
            sessionProperties,
            mapOf(),
            mockTimeoutCallback
        )

        val message = deliveryService.sentMoments.single()
        assertEquals(result.event.eventId, message.event.eventId)
    }

    @Test
    fun `verify onEventStarted prevents send the event if feature gating gates it`() {
        val eventId = "event-id"
        val eventName = "event-name"
        val startTime = 123L
        val threshold = 100L
        val mockTimeoutCallback = Runnable {}
        clock.setCurrentTime(456)

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
            sessionProperties,
            mapOf(),
            mockTimeoutCallback
        )
        assertTrue(deliveryService.sentMoments.isEmpty())
    }

    @Test
    fun `verify onEventStarted sends the event if feature gating allows it`() {
        val eventId = "event-id"
        val eventName = "event-name"
        val startTime = 123L
        val threshold = 100L
        val mockTimeoutCallback = Runnable {}
        clock.setCurrentTime(456)

        cfg = RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                sessionComponents = setOf("s_mts")
            ),
            eventLimits = mapOf(eventId to threshold)
        )

        val result = eventHandler.onEventStarted(
            eventId,
            eventName,
            startTime,
            sessionProperties,
            mapOf(),
            mockTimeoutCallback
        )
        val message = deliveryService.sentMoments.single()
        assertEquals(result.event.eventId, message.event.eventId)
    }

    @Test
    fun `verify onEventEnded prevents send event if feature gating gates it`() {
        val eventProperties = mapOf<String, Any>()
        val startTime = 100L
        val endTime = 300L
        clock.setCurrentTime(endTime)
        val originEventId = "origin-event-id"
        val originEventName = "origin-event-name"

        val originEvent = Event(
            timestamp = startTime,
            eventId = originEventId,
            name = originEventName,
            type = EventType.START
        )
        val originEventDescription = EventDescription(mockLateTimer, originEvent)
        cfg = createGatingConfig(emptySet())

        eventHandler.onEventEnded(
            originEventDescription,
            false,
            eventProperties,
            sessionProperties
        )
        assertTrue(deliveryService.sentMoments.isEmpty())
    }

    @Test
    fun `verify onEventEnded sends event if feature gating allows it`() {
        val eventProperties = mapOf<String, Any>()
        val startTime = 100L
        val endTime = 300L
        clock.setCurrentTime(endTime)
        val originEventId = "origin-event-id"
        val originEventName = "origin-event-name"

        val originEvent = Event(
            timestamp = startTime,
            eventId = originEventId,
            name = originEventName,
            type = EventType.START
        )
        val originEventDescription = EventDescription(mockLateTimer, originEvent)
        cfg = createGatingConfig(setOf("s_mts"))

        val result = eventHandler.onEventEnded(
            originEventDescription,
            false,
            eventProperties,
            sessionProperties
        )
        assertEquals(result, deliveryService.sentMoments.last())
    }

    @Test
    fun `verify onEventEnded prevents send startup event if feature gating gates it`() {
        val eventProperties = mapOf<String, Any>()
        val startTime = 100L
        val endTime = 300L
        clock.setCurrentTime(endTime)
        val originEventId = "origin-event-id"
        val originEventName = EmbraceEventService.STARTUP_EVENT_NAME

        val originEvent = Event(
            timestamp = startTime,
            eventId = originEventId,
            name = originEventName,
            type = EventType.START
        )
        val originEventDescription = EventDescription(mockLateTimer, originEvent)
        cfg = createGatingConfig(emptySet())

        eventHandler.onEventEnded(
            originEventDescription,
            false,
            eventProperties,
            sessionProperties
        )

        assertTrue(deliveryService.sentMoments.isEmpty())
    }

    @Test
    fun `verify onEventEnded sends startup event if feature gating allows it`() {
        val eventProperties = mapOf<String, Any>()
        val startTime = 100L
        val endTime = 300L
        clock.setCurrentTime(endTime)
        val originEventId = "origin-event-id"
        val originEventName = EmbraceEventService.STARTUP_EVENT_NAME

        val originEvent = Event(
            timestamp = startTime,
            eventId = originEventId,
            name = originEventName,
            type = EventType.START
        )
        val originEventDescription = EventDescription(mockLateTimer, originEvent)
        createGatingConfig(setOf("s_mts"))

        val result = eventHandler.onEventEnded(
            originEventDescription,
            false,
            eventProperties,
            sessionProperties
        )
        assertEquals(result, deliveryService.sentMoments.last())
    }

    private fun createGatingConfig(components: Set<String>) = RemoteConfig(
        sessionConfig = SessionRemoteConfig(
            sessionComponents = components
        )
    )
}
