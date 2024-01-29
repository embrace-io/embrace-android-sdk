package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.LocalConfigParser
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.fakeBackgroundActivity
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePerformanceInfoService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.FakeThermalStatusService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.worker.ScheduledWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class EmbraceBackgroundActivityServiceTest {

    private val initial = fakeBackgroundActivity()
    private lateinit var service: EmbraceBackgroundActivityService
    private lateinit var clock: FakeClock
    private lateinit var performanceInfoService: FakePerformanceInfoService
    private lateinit var metadataService: MetadataService
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var breadcrumbService: FakeBreadcrumbService
    private lateinit var activityService: FakeProcessStateService
    private lateinit var eventService: EventService
    private lateinit var logMessageService: LogMessageService
    private lateinit var userService: UserService
    private lateinit var internalErrorService: InternalErrorService
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var ndkService: FakeNdkService
    private lateinit var configService: FakeConfigService
    private lateinit var localConfig: LocalConfig
    private lateinit var spansService: EmbraceSpansService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var blockingExecutorService: BlockingScheduledExecutorService

    @Before
    fun init() {
        clock = FakeClock(10000L)
        performanceInfoService = FakePerformanceInfoService()
        metadataService = FakeMetadataService()
        sessionIdTracker = FakeSessionIdTracker()
        breadcrumbService = FakeBreadcrumbService()
        activityService = FakeProcessStateService(isInBackground = true)
        eventService = FakeEventService()
        logMessageService = FakeLogMessageService()
        internalErrorService = FakeInternalErrorService()
        deliveryService = FakeDeliveryService()
        ndkService = FakeNdkService()
        preferencesService = FakePreferenceService(backgroundActivityEnabled = true)
        userService = FakeUserService()
        spansService = EmbraceSpansService(
            clock = OpenTelemetryClock(embraceClock = clock),
            telemetryService = FakeTelemetryService()
        )
        configService = FakeConfigService(
            backgroundActivityCaptureEnabled = true
        )
        configService.updateListeners()
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"background_activity\": {\"max_background_activity_seconds\": 3600}}",
            EmbraceSerializer()
        )

        blockingExecutorService = BlockingScheduledExecutorService(blockingMode = false)
    }

    @Test
    fun `test background activity state when going to the background`() {
        this.service = createService(createInitialSession = false)
        service.startBackgroundActivityWithState(clock.now(), false)

        val payload = deliveryService.lastSavedBackgroundActivities.single().session
        assertEquals(Session.LifeEventType.BKGND_STATE, payload.startType)
        assertEquals(5, payload.number)
        assertFalse(payload.isColdStart)
    }

    @Test
    fun `test background activity state when going to the foreground`() {
        this.service = createService()
        val timestamp = 1669392000L
        service.endBackgroundActivityWithState(initial, timestamp)

        assertEquals(2, deliveryService.saveBackgroundActivityInvokedCount)
        assertEquals(1, deliveryService.sendBackgroundActivitiesInvokedCount)
        val payload = checkNotNull(deliveryService.lastSavedBackgroundActivities.last())
        assertEquals(1, payload.session.number)
        assertFalse(payload.session.isColdStart)
    }

    @Test
    fun `background activity is not started whn the service initializes in the foreground`() {
        activityService.isInBackground = false
        this.service = createService(false)
        assertTrue(deliveryService.lastSavedBackgroundActivities.isEmpty())
    }

    @Test
    fun `activity is cached on start capture`() {
        this.service = createService()
        assertNotNull(deliveryService.lastSavedBackgroundActivities.single())
    }

    @Test
    fun `activity is cached when going to the foreground regardless of time limit`() {
        val startTime = clock.now()

        this.service = createService()
        assertNotNull(deliveryService.lastSavedBackgroundActivities.single())
        assertEquals(1, deliveryService.saveBackgroundActivityInvokedCount)

        clock.setCurrentTime(startTime + 1000)
        service.endBackgroundActivityWithState(initial, startTime + 1000)
        assertEquals(2, deliveryService.saveBackgroundActivityInvokedCount)

        clock.setCurrentTime(startTime + 5000)
        service.startBackgroundActivityWithState(startTime + 5000, false)
        assertEquals(3, deliveryService.saveBackgroundActivityInvokedCount)
    }

    @Test
    fun `activity is cached when delay completes`() {
        blockingExecutorService = BlockingScheduledExecutorService(blockingMode = true)

        this.service = createService()
        blockingExecutorService.runCurrentlyBlocked()
        assertNotNull(deliveryService.lastSavedBackgroundActivities.single())
        assertEquals(1, deliveryService.saveBackgroundActivityInvokedCount)

        clock.tick(1000)
        blockingExecutorService.runCurrentlyBlocked()
        service.endBackgroundActivityWithState(initial, clock.now())
        assertEquals(2, deliveryService.saveBackgroundActivityInvokedCount)

        // tick 4999 milliseconds, the activity should not be cached yet
        clock.tick(4999)
        blockingExecutorService.runCurrentlyBlocked()
        service.startBackgroundActivityWithState(clock.now(), false)
        assertEquals(2, deliveryService.saveBackgroundActivityInvokedCount)

        // tick an extra millisecond, the delayed job should execute
        clock.tick(1)
        blockingExecutorService.runCurrentlyBlocked()
        assertEquals(3, deliveryService.saveBackgroundActivityInvokedCount)
    }

    @Test
    fun `activity is cached on start capture when the service started in foreground`() {
        activityService.isInBackground = false
        this.service = createService(false)

        assertNull(deliveryService.lastSavedBackgroundActivities.singleOrNull())

        service.startBackgroundActivityWithState(clock.now(), false)

        assertNotNull(deliveryService.lastSavedBackgroundActivities.single())
    }

    @Test
    fun `calling save() persists the background activity in cache`() {
        activityService.isInBackground = false // start the service in foreground
        val startTime = clock.now()
        clock.setCurrentTime(startTime)

        this.service = createService(false)
        assertEquals(0, deliveryService.saveBackgroundActivityInvokedCount)

        // start capturing background activity 10 seconds later, activity is first cached
        service.startBackgroundActivityWithState(startTime + 10 * 1000, false)
        assertEquals(1, deliveryService.saveBackgroundActivityInvokedCount)

        // elapse another 10 seconds to get around the 5 seconds limitation
        clock.setCurrentTime(startTime + 20 * 1000)
        service.saveBackgroundActivitySnapshot(initial)

        assertEquals(2, deliveryService.saveBackgroundActivityInvokedCount)
    }

    @Test
    fun `save() does not persist to disk if the activity was cached within the last 5 seconds`() {
        activityService.isInBackground = false // start the service in foreground

        this.service = createService(false)
        assertEquals(0, deliveryService.saveBackgroundActivityInvokedCount)

        service.startBackgroundActivityWithState(clock.now(), false)
        assertEquals(1, deliveryService.saveBackgroundActivityInvokedCount)

        // save() will not persist to disk since the last time was less than 5 seconds ago
        service.saveBackgroundActivitySnapshot(initial)
        assertEquals(1, deliveryService.saveBackgroundActivityInvokedCount)
    }

    @Test
    fun `saving will persist the current completed spans but will not flush`() {
        val startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.now())
        service = createService()
        spansService.initializeService(startTimeNanos)
        clock.tick(1)
        spansService.recordCompletedSpan(
            name = "test-span",
            startTimeNanos = startTimeNanos,
            endTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.now())
        )
        assertEquals(1, spansService.completedSpans()?.size)
        // move time ahead so the save will actually persist the new background activity message
        clock.tick(6000)
        service.saveBackgroundActivitySnapshot(initial)
        assertNotNull(deliveryService.lastSavedBackgroundActivities.last())
        assertEquals(2, deliveryService.saveBackgroundActivityInvokedCount)
        assertEquals(1, deliveryService.lastSavedBackgroundActivities.last().spans?.size)
        assertEquals(1, spansService.completedSpans()?.size)
    }

    @Test
    fun `crash will save and flush the current completed spans`() {
        // Prevent background thread from overwriting deliveryService.lastSavedBackgroundActivity
        blockingExecutorService = BlockingScheduledExecutorService(blockingMode = true)
        service = createService()
        val now = TimeUnit.MILLISECONDS.toNanos(clock.now())
        spansService.initializeService(now)
        service.endBackgroundActivityWithCrash(initial, now, "crashId")
        assertNotNull(deliveryService.lastSavedBackgroundActivities.single())

        // there should be 1 completed span: the session span
        assertEquals(1, deliveryService.saveBackgroundActivityInvokedCount)
        assertEquals(1, deliveryService.lastSavedBackgroundActivities.single().spans?.size)
        assertEquals(0, spansService.completedSpans()?.size)
    }

    @Test
    fun `foregrounding will flush the current completed spans`() {
        service = createService()
        spansService.initializeService(TimeUnit.MILLISECONDS.toNanos(clock.now()))
        service.endBackgroundActivityWithState(initial, clock.now())
        assertNotNull(deliveryService.lastSavedBackgroundActivities.last())

        // there should be 1 completed span: the session span
        assertEquals(1, deliveryService.lastSavedBackgroundActivities.last().spans?.size)
        assertEquals(0, spansService.completedSpans()?.size)
    }

    @Test
    fun `sending background activity will flush the current completed spans`() {
        service = createService()
        spansService.initializeService(TimeUnit.MILLISECONDS.toNanos(clock.now()))
        clock.tick(1000L)
        service.endBackgroundActivityWithState(initial, clock.now())
        val msg = deliveryService.lastSentBackgroundActivities.last()

        // there should be 1 completed span: the session span
        assertEquals(1, msg.spans?.size)
        assertEquals(0, spansService.completedSpans()?.size)
    }

    @Test
    fun `foregrounding background activity flushes breadcrumbs`() {
        service = createService()
        clock.tick(1000L)
        service.endBackgroundActivityWithState(initial, clock.now())
        assertNotNull(deliveryService.lastSavedBackgroundActivities.last())
        assertEquals(1, breadcrumbService.flushCount)
    }

    @Test
    fun `saving background activity in the background will not flush breadcrumbs`() {
        service = createService()
        clock.tick(1000L)
        service.saveBackgroundActivitySnapshot(initial)
        assertNotNull(deliveryService.lastSavedBackgroundActivities.single())
        assertEquals(0, breadcrumbService.flushCount)
    }

    @Test
    fun `background activity capture disabled after onBackground`() {
        service = createService(createInitialSession = false)

        service.startBackgroundActivityWithState(clock.now(), true)
        clock.tick(1000L)

        // missing end call simulates service being enabled halfway through.
        assertEquals(1, deliveryService.lastSavedBackgroundActivities.size)
        assertEquals(0, deliveryService.lastSentBackgroundActivities.size)

        // next BA is recorded correctly
        service.startBackgroundActivityWithState(clock.now(), false)
        clock.tick(1000L)
        service.endBackgroundActivityWithState(initial, clock.now())
        assertEquals(2, deliveryService.lastSavedBackgroundActivities.size)
        assertEquals(2, deliveryService.lastSentBackgroundActivities.size)
    }

    private fun createService(createInitialSession: Boolean = true): EmbraceBackgroundActivityService {
        val collator = PayloadMessageCollator(
            configService,
            metadataService,
            eventService,
            logMessageService,
            internalErrorService,
            performanceInfoService,
            FakeWebViewService(),
            FakeThermalStatusService(),
            null,
            breadcrumbService,
            userService,
            preferencesService,
            spansService,
            clock,
            FakeSessionPropertiesService(),
            FakeStartupService()
        )
        val cacher = PeriodicBackgroundActivityCacher(
            clock,
            ScheduledWorker(blockingExecutorService)
        )
        return EmbraceBackgroundActivityService(
            deliveryService,
            collator,
            clock,
            cacher,
            Any(),
        ).apply {
            if (createInitialSession) {
                startBackgroundActivityWithState(clock.now(), true)
            }
        }
    }
}
