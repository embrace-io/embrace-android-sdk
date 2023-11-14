package io.embrace.android.embracesdk.event

import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.FakeWorkerThreadModule
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.config.local.StartupMomentLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SpansRemoteConfig
import io.embrace.android.embracesdk.event.EmbraceEventService.Companion.STARTUP_EVENT_NAME
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakePerformanceInfoService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.fakes.fakeSpansBehavior
import io.embrace.android.embracesdk.fakes.fakeStartupBehavior
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.EmbraceSessionProperties
import io.embrace.android.embracesdk.session.MemoryCleanerService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.worker.ExecutorName
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class EmbraceEventServiceTest {

    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var configService: FakeConfigService
    private lateinit var gatingService: GatingService
    private lateinit var fakeWorkerThreadModule: FakeWorkerThreadModule
    private lateinit var spansService: EmbraceSpansService
    private lateinit var sessionProperties: EmbraceSessionProperties
    private lateinit var eventService: EmbraceEventService
    private lateinit var fakeClock: FakeClock
    private lateinit var eventHandler: EventHandler
    private lateinit var startupMomentLocalConfig: StartupMomentLocalConfig
    private lateinit var remoteConfig: RemoteConfig

    companion object {
        private lateinit var metadataService: MetadataService
        private lateinit var preferenceService: PreferencesService
        private lateinit var performanceInfoService: PerformanceInfoService
        private lateinit var userService: UserService
        private lateinit var processStateService: ProcessStateService
        private lateinit var mockMemoryCleanerService: MemoryCleanerService
        private lateinit var logger: InternalEmbraceLogger

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            metadataService = FakeAndroidMetadataService()
            preferenceService = FakePreferenceService()
            performanceInfoService = FakePerformanceInfoService()
            processStateService = FakeProcessStateService()
            mockMemoryCleanerService = mockk(relaxUnitFun = true)
            logger = InternalEmbraceLogger()
            userService = EmbraceUserService(
                preferencesService = preferenceService,
                logger = logger
            )
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            unmockkAll()
        }
    }

    @Before
    fun before() {
        clearAllMocks(
            answers = false,
            objectMocks = false,
            constructorMocks = false,
            staticMocks = false
        )

        fakeClock = FakeClock()
        fakeClock.setCurrentTime(10L)
        remoteConfig = RemoteConfig()
        deliveryService = FakeDeliveryService()
        startupMomentLocalConfig = StartupMomentLocalConfig()
        configService = FakeConfigService(
            spansBehavior = fakeSpansBehavior { SpansRemoteConfig(pctEnabled = 100f) },
            startupBehavior = fakeStartupBehavior { startupMomentLocalConfig },
            dataCaptureEventBehavior = fakeDataCaptureEventBehavior { remoteConfig }
        )
        sessionProperties = EmbraceSessionProperties(
            FakePreferenceService(),
            logger,
            configService
        )
        gatingService = FakeGatingService(configService)
        fakeWorkerThreadModule = FakeWorkerThreadModule(clock = fakeClock, blockingMode = true)
        spansService = EmbraceSpansService(clock = OpenTelemetryClock(embraceClock = fakeClock))
        configService.addListener(spansService)
        eventHandler = EventHandler(
            metadataService = metadataService,
            configService = configService,
            userService = userService,
            performanceInfoService = performanceInfoService,
            deliveryService = deliveryService,
            logger = logger,
            clock = fakeClock,
            scheduledExecutor = fakeWorkerThreadModule.scheduledExecutor(ExecutorName.SCHEDULED_REGISTRATION)
        )
        eventService = EmbraceEventService(
            1,
            deliveryService,
            configService,
            metadataService,
            performanceInfoService,
            userService,
            sessionProperties,
            logger,
            fakeWorkerThreadModule,
            fakeClock,
            spansService
        )
        startupMomentLocalConfig = StartupMomentLocalConfig()
        eventService.eventHandler = eventHandler
    }

    @Test
    fun `if event is not allowed to start it should not continue processing event`() {
        val disabledEvent = "disabled-event"
        remoteConfig = RemoteConfig(
            disabledEventAndLogPatterns = setOf("disabled-event")
        )
        eventService.startEvent(disabledEvent)
        assertFalse(eventService.activeEvents.containsKey(disabledEvent))
    }

    @Test
    fun `verify an event is started successfully with the expected internal keys`() {
        val eventNames = listOf("event-to-start", "another-event-to-start", "yet-another-event-to-start")
        val identifiers = listOf("identifier", null, "")
        repeat(3) {
            eventService.startEvent(eventNames[it], identifiers[it])
            assertNotNull(eventService.getActiveEvent(eventNames[it], identifiers[it]))
        }
        assertTrue(eventService.activeEvents.containsKey("${eventNames[0]}#${identifiers[0]}"))
        assertTrue(eventService.activeEvents.containsKey(eventNames[1]))
        assertTrue(eventService.activeEvents.containsKey(eventNames[2]))
    }

    @Test
    fun `verify an event is started successfully for startEvent method specifying screenshotting`() {
        val eventName = "event-to-start"
        eventService.startEvent(eventName, null)
        val eventDescription = eventService.getActiveEvent(eventName, null)
        assertNotNull(eventDescription)
    }

    @Test
    fun `verify an event is started successfully for start method with custom properties`() {
        val eventName = "event-to-start"
        val customProperties = mapOf("u" to "orns")
        eventService.startEvent(eventName, null, customProperties)
        val eventDescription = eventService.getActiveEvent(eventName, null)
        assertNotNull(eventDescription)
        val eventProperties = eventDescription?.event?.customPropertiesMap
        checkNotNull(eventProperties)
        assertEquals(customProperties.size, eventProperties.size)
        customProperties.forEach {
            assertEquals(it.value, eventProperties[it.key])
        }
    }

    @Test
    fun `verify an event is started successfully for start method with screenshotting and custom properties`() {
        val eventName = "event-to-start"
        val customProperties = mapOf("u" to "orns")
        eventService.startEvent(eventName, null, customProperties)
        val eventDescription = eventService.getActiveEvent(eventName, null)
        assertNotNull(eventDescription)
        val eventProperties = eventDescription?.event?.customPropertiesMap
        checkNotNull(eventProperties)
        assertEquals(customProperties.size, eventProperties.size)
        customProperties.forEach {
            assertEquals(it.value, eventProperties[it.key])
        }
    }

    @Test
    fun `if event is not allowed to end it should not continue processing event`() {
        val disabledEvent = "disabled-event"
        remoteConfig = RemoteConfig(
            disabledEventAndLogPatterns = setOf("disabled-event")
        )
        eventService.endEvent(disabledEvent)
        assertNull(deliveryService.lastEventSentAsync)
    }

    @Test
    fun `verify an event without an identifier ended successfully`() {
        val eventName = "event-to-end"
        eventService.startEvent(eventName)
        assertNotNull(deliveryService.lastEventSentAsync)
        assertEquals(EmbraceEvent.Type.START, deliveryService.lastEventSentAsync?.event?.type)
        eventService.endEvent(eventName)
        assertEquals(EmbraceEvent.Type.END, deliveryService.lastEventSentAsync?.event?.type)
    }

    @Test
    fun `verify an event with identifier is ended successfully`() {
        val eventName = "event-to-end"
        val identifier = "identifier"
        eventService.startEvent(eventName, identifier)
        assertEquals(EmbraceEvent.Type.START, deliveryService.lastEventSentAsync?.event?.type)
        eventService.endEvent(eventName, identifier)
        assertEquals(EmbraceEvent.Type.END, deliveryService.lastEventSentAsync?.event?.type)
    }

    @Test
    fun `verify an event with custom properties is ended successfully`() {
        val eventName = "event-to-end"
        val customProperties = mapOf("yel" to "lows")
        eventService.startEvent(eventName)
        assertEquals(EmbraceEvent.Type.START, deliveryService.lastEventSentAsync?.event?.type)
        eventService.endEvent(eventName, customProperties)
        assertEquals(EmbraceEvent.Type.END, deliveryService.lastEventSentAsync?.event?.type)
        val eventProperties = deliveryService.lastEventSentAsync?.event?.customPropertiesMap
        checkNotNull(eventProperties)
        assertEquals(customProperties.size, eventProperties.size)
        customProperties.forEach {
            assertEquals(it.value, eventProperties[it.key])
        }
    }

    @Test
    fun `verify an event with an identifier and custom properties ended successfully`() {
        val eventName = "event-to-end"
        val customProperties = mapOf("yel" to "lows")
        eventService.startEvent(eventName)
        assertEquals(EmbraceEvent.Type.START, deliveryService.lastEventSentAsync?.event?.type)
        eventService.endEvent(eventName, customProperties)
        assertEquals(EmbraceEvent.Type.END, deliveryService.lastEventSentAsync?.event?.type)
        val eventProperties = deliveryService.lastEventSentAsync?.event?.customPropertiesMap
        checkNotNull(eventProperties)
        assertEquals(customProperties.size, eventProperties.size)
        customProperties.forEach {
            assertEquals(it.value, eventProperties[it.key])
        }
    }

    @Test
    fun `verify an event is ended successfully for a startup event`() {
        eventService.startEvent(STARTUP_EVENT_NAME)
        val originalEvent = checkNotNull(eventService.getActiveEvent(STARTUP_EVENT_NAME, null))
        fakeClock.setCurrentTime(25L)
        eventService.endEvent(STARTUP_EVENT_NAME)
        assertNotNull(eventService.getStartupMomentInfo())
        assertEquals(15L, eventService.getStartupMomentInfo()?.duration)
        assertEquals(originalEvent.event.lateThreshold, eventService.getStartupMomentInfo()?.threshold)
    }

    @Test
    fun `verify send a startup moment successfully`() {
        eventService.sendStartupMoment()
        assertNotNull(eventService.getActiveEvent(STARTUP_EVENT_NAME, null))
    }

    @Test
    fun `sending a startup moment twice, should not do anything the 2nd time`() {
        eventService.sendStartupMoment()
        assertEquals(1, deliveryService.eventSentAsyncInvokedCount)
        eventService.sendStartupMoment()
        assertEquals(1, deliveryService.eventSentAsyncInvokedCount)
    }

    @Test
    fun `verify onForeground for a cold start sends a startup moment`() {
        eventService.onForeground(true, 123, 456)
        assertNotNull(eventService.getActiveEvent(STARTUP_EVENT_NAME, null))
        val lastEvent = deliveryService.lastEventSentAsync
        assertEquals(1, deliveryService.eventSentAsyncInvokedCount)
        assertNotNull(lastEvent)
        assertEquals(EmbraceEvent.Type.START, lastEvent?.event?.type)
        assertEquals(STARTUP_EVENT_NAME, lastEvent?.event?.name)
    }

    @Test
    fun `verify onForeground for a non cold start does not do anything`() {
        eventService.onForeground(false, 123, 456)
        assertNull(eventService.getActiveEvent(STARTUP_EVENT_NAME, null))
        assertNull(deliveryService.lastEventSentAsync)
    }

    @Test
    fun `applicationStartupComplete if automatically end is enabled ends startup event`() {
        eventService.startEvent(STARTUP_EVENT_NAME)
        eventService.applicationStartupComplete()
        val lastEvent = deliveryService.lastEventSentAsync
        assertEquals(2, deliveryService.eventSentAsyncInvokedCount)
        assertNotNull(lastEvent)
        assertEquals(EmbraceEvent.Type.END, lastEvent?.event?.type)
        assertEquals(STARTUP_EVENT_NAME, lastEvent?.event?.name)
    }

    @Test
    fun `applicationStartupComplete if automatically end is disabled does not do anything`() {
        startupMomentLocalConfig = StartupMomentLocalConfig(automaticallyEnd = false)
        eventService.applicationStartupComplete()
        assertNull(deliveryService.lastEventSentAsync)
    }

    @Test
    fun `verify we are getting active event ids`() {
        val eventName = "event-to-start"
        eventService.startEvent(eventName)
        assertTrue(eventService.getActiveEventIds().size == 1)
        val event = checkNotNull(eventService.getActiveEvent(eventName, null))
        assertEquals(event.event.eventId, eventService.getActiveEventIds()[0])
    }

    @Test
    fun `verify no active events if no event has been started`() {
        assertTrue(eventService.getActiveEventIds().size == 0)
    }

    @Test
    fun `verify no startup event info is available if no startup event has been started`() {
        assertNull(eventService.getStartupMomentInfo())
    }

    @Test
    fun `verify find event ids using findEventIdsForSession()`() {
        // Simulate the session moving forward in time, and having new Moments added and us retrieving the eventIds given the new time range
        // Note that because of how the underlying cache works, if the size of the eventIds collection didn't change from the last time
        // this method was invoked, the previously cached value will be returned. So while calling this method with arbitrary start/end
        // times can return wrong values, how it is being used, that won't happen. This test will simulate the EXPECTED usage rather than
        // the arbitrary usage.

        val sessionBeginTime = 100L
        fakeClock.setCurrentTime(sessionBeginTime)
        eventService.startEvent("first")
        fakeClock.setCurrentTime(fakeClock.now() + 1L)

        // after a new Moment is logged and the time ticks forward, we should see it reflected in the cache
        assertEquals(1, eventService.findEventIdsForSession(sessionBeginTime, fakeClock.now()).size)
        fakeClock.setCurrentTime(fakeClock.now() + 50L)
        eventService.startEvent("second")
        fakeClock.setCurrentTime(fakeClock.now() + 1L)
        eventService.startEvent("third")

        // the new time range will only return 2 of the logged moments because the clock hasn't ticked forward
        assertEquals(2, eventService.findEventIdsForSession(sessionBeginTime, fakeClock.now()).size)
        fakeClock.setCurrentTime(fakeClock.now() + 1L)

        // After the clock ticks forward, because of the caching, we will still only return 2. This is a perf optimization that will be
        // OK in practice because we should only bust the cache if there's a new moment - this check is just to verify the caching works
        // because it won't really happen in practice
        assertEquals(2, eventService.findEventIdsForSession(sessionBeginTime, fakeClock.now()).size)

        // After logging another moment, the cache is busted so after the clock ticks forward, we get all 4 moments
        eventService.startEvent("fourth")
        fakeClock.setCurrentTime(fakeClock.now() + 1L)
        assertEquals(4, eventService.findEventIdsForSession(sessionBeginTime, fakeClock.now()).size)
    }

    @Test
    fun `verify close clears the existing events`() {
        eventService.startEvent("event-yeah")
        eventService.close()
        assertTrue(eventService.activeEvents.isEmpty())
    }

    @Test
    fun `verify clean collections`() {
        val time = 123L
        fakeClock.setCurrentTime(time)
        val eventName = "event-to-start"
        val identifier = "identifier"

        eventService.startEvent(eventName, identifier)
        // assert that active events is not empty
        assertTrue(eventService.activeEvents.isNotEmpty())
        assertTrue(eventService.findEventIdsForSession(time - 1, time + 1).isNotEmpty())

        eventService.cleanCollections()

        assertTrue(eventService.activeEvents.isEmpty())
        assertTrue(eventService.findEventIdsForSession(time - 1, time + 1).isEmpty())
    }

    @Test
    fun `startup event name is case sensitive`() {
        eventService.startEvent(STARTUP_EVENT_NAME.toUpperCase())
        assertNull(eventService.getActiveEvent(STARTUP_EVENT_NAME, null))
        eventService.applicationStartupComplete()
        val lastEvent = deliveryService.lastEventSentAsync
        assertEquals(1, deliveryService.eventSentAsyncInvokedCount)
        assertNotEquals(EmbraceEvent.Type.END, lastEvent?.event?.type)
        assertNotEquals(STARTUP_EVENT_NAME, lastEvent?.event?.name)
    }

    @Test
    fun `startup logged as span if startup moment automatic end is enabled`() {
        spansService.initializeService(
            sdkInitStartTimeNanos = TimeUnit.MILLISECONDS.toNanos(1),
            sdkInitEndTimeNanos = TimeUnit.MILLISECONDS.toNanos(3)
        )
        configService.updateListeners()
        spansService.flushSpans()
        eventService.sendStartupMoment()
        eventService.applicationStartupComplete()
        val executor = fakeWorkerThreadModule.backgroundExecutor(ExecutorName.BACKGROUND_REGISTRATION)
        executor.runCurrentlyBlocked()
        val completedSpans = checkNotNull(spansService.completedSpans())
        assertEquals(1, completedSpans.size)
        with(completedSpans[0]) {
            assertEquals("emb-startup-moment", name)
            assertEquals(TimeUnit.MILLISECONDS.toNanos(1), startTimeNanos)
            assertEquals(TimeUnit.MILLISECONDS.toNanos(10), endTimeNanos)
        }
    }

    @Test
    fun `startup logged as span if when startup moment manually ends`() {
        startupMomentLocalConfig = StartupMomentLocalConfig(automaticallyEnd = false)
        spansService.initializeService(
            sdkInitStartTimeNanos = TimeUnit.MILLISECONDS.toNanos(1),
            sdkInitEndTimeNanos = TimeUnit.MILLISECONDS.toNanos(3)
        )
        configService.updateListeners()
        spansService.flushSpans()
        eventService.sendStartupMoment()
        eventService.applicationStartupComplete()
        val executor = fakeWorkerThreadModule.backgroundExecutor(ExecutorName.BACKGROUND_REGISTRATION)
        executor.runCurrentlyBlocked()
        val completedSpans = checkNotNull(spansService.completedSpans())
        assertEquals(0, completedSpans.size)

        fakeClock.setCurrentTime(20L)
        eventService.endEvent(STARTUP_EVENT_NAME)
        executor.runCurrentlyBlocked()
        val completedSpansAgain = checkNotNull(spansService.completedSpans())
        assertEquals(1, completedSpansAgain.size)

        with(completedSpansAgain[0]) {
            assertEquals("emb-startup-moment", name)
            assertEquals(TimeUnit.MILLISECONDS.toNanos(1), startTimeNanos)
            assertEquals(TimeUnit.MILLISECONDS.toNanos(20), endTimeNanos)
        }
    }

    @Test
    fun `startup not logged as span if startup moment is ended via a timeout`() {
        spansService.initializeService(
            sdkInitStartTimeNanos = TimeUnit.MILLISECONDS.toNanos(1),
            sdkInitEndTimeNanos = TimeUnit.MILLISECONDS.toNanos(3)
        )
        configService.updateListeners()
        spansService.flushSpans()
        eventService.sendStartupMoment()
        assertNull(eventService.getStartupMomentInfo())
        fakeClock.tick(10000L)
        fakeWorkerThreadModule.backgroundExecutor(ExecutorName.BACKGROUND_REGISTRATION).runCurrentlyBlocked()
        fakeWorkerThreadModule.scheduledExecutor(ExecutorName.SCHEDULED_REGISTRATION).runCurrentlyBlocked()
        assertNotNull(eventService.getStartupMomentInfo())
        val completedSpans = checkNotNull(spansService.completedSpans())
        assertEquals(0, completedSpans.size)
    }
}
