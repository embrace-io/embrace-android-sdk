package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.SpansRemoteConfig
import io.embrace.android.embracesdk.event.EmbraceRemoteLogger
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeSpansBehavior
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.BackgroundActivity
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class EmbraceBackgroundActivityServiceTest {

    private lateinit var service: EmbraceBackgroundActivityService
    private lateinit var clock: FakeClock
    private lateinit var performanceInfoService: PerformanceInfoService
    private lateinit var metadataService: MetadataService
    private lateinit var mockBreadcrumbService: BreadcrumbService
    private lateinit var activityService: FakeProcessStateService
    private lateinit var eventService: EventService
    private lateinit var remoteLogger: EmbraceRemoteLogger
    private lateinit var userService: UserService
    private lateinit var exceptionService: EmbraceInternalErrorService
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var ndkService: NdkService
    private lateinit var configService: FakeConfigService
    private lateinit var localConfig: LocalConfig
    private lateinit var spansService: EmbraceSpansService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var blockableExecutorService: BlockableExecutorService
    private lateinit var spansRemoteConfig: SpansRemoteConfig

    @Before
    fun init() {
        clock = FakeClock(10000L)
        performanceInfoService = mockk()
        metadataService = FakeAndroidMetadataService()
        mockBreadcrumbService = mockk(relaxed = true)
        activityService = FakeProcessStateService(isInBackground = true)
        eventService = mockk()
        remoteLogger = mockk()
        exceptionService = mockk()
        deliveryService = FakeDeliveryService()
        ndkService = mockk(relaxed = true)
        preferencesService = FakePreferenceService(backgroundActivityEnabled = true)
        userService = EmbraceUserService(
            preferencesService,
            mockk()
        )
        spansService = EmbraceSpansService(clock = OpenTelemetryClock(embraceClock = clock))
        spansRemoteConfig = SpansRemoteConfig(pctEnabled = 100f)
        configService = FakeConfigService(
            backgroundActivityCaptureEnabled = true,
            spansBehavior = fakeSpansBehavior { spansRemoteConfig }
        )
        configService.addListener(spansService)
        configService.updateListeners()
        localConfig = spyk(
            LocalConfig.buildConfig(
                "GrCPU",
                false,
                "{\"background_activity\": {\"max_background_activity_seconds\": 3600}}",
                EmbraceSerializer()
            )
        )
        blockableExecutorService = BlockableExecutorService()

        every { eventService.findEventIdsForSession(any(), any()) } returns listOf()
        every { remoteLogger.findInfoLogIds(any(), any()) } returns listOf()
        every { remoteLogger.findWarningLogIds(any(), any()) } returns listOf()
        every { remoteLogger.findErrorLogIds(any(), any()) } returns listOf()
        every { remoteLogger.getInfoLogsAttemptedToSend() } returns 0
        every { remoteLogger.getWarnLogsAttemptedToSend() } returns 0
        every { remoteLogger.getErrorLogsAttemptedToSend() } returns 0
        every { remoteLogger.getUnhandledExceptionsSent() } returns 0
        every { exceptionService.currentExceptionError } returns mockk()
        every {
            performanceInfoService.getSessionPerformanceInfo(
                any(),
                any(),
                any(),
                null
            )
        } returns mockk()
    }

    @Test
    fun `test that the service listens to activity events`() {
        this.service = createService()
        assertEquals(service, activityService.listeners.single())
    }

    @Test
    fun `test background activity state when going to the background`() {
        this.service = createService()

        service.onBackground(clock.now())

        val payload = checkNotNull(service.backgroundActivity)
        assertEquals(BackgroundActivity.LifeEventType.BKGND_STATE, payload.startType)
        assertEquals(5, payload.number)
        assertEquals(payload.sessionId, metadataService.activeSessionId)
    }

    @Test
    fun `test background activity state when going to the foreground`() {
        this.service = createService()

        val timestamp = 1669392000L

        service.onForeground(true, 123, timestamp)

        assertNull(service.backgroundActivity)

        assertEquals(2, deliveryService.saveBackgroundActivityInvokedCount)
        assertEquals(1, deliveryService.sendBackgroundActivitiesInvokedCount)
        val payload = checkNotNull(deliveryService.lastSavedBackgroundActivity)
        assertEquals(5, payload.backgroundActivity.number)
    }

    @Test
    fun `background activity is not started whn the service initializes in the foreground`() {
        activityService.isInBackground = false
        this.service = createService()
        assertNull(service.backgroundActivity)
    }

    @Test
    fun `activity is cached on start capture`() {
        this.service = createService()
        assertNotNull(deliveryService.lastSavedBackgroundActivity)
    }

    @Test
    fun `activity is cached when going to the foreground regardless of time limit`() {
        val startTime = clock.now()

        this.service = createService()
        assertNotNull(deliveryService.lastSavedBackgroundActivity)
        assertEquals(1, deliveryService.saveBackgroundActivityInvokedCount)

        clock.setCurrentTime(startTime + 1000)
        service.onForeground(true, 500, startTime + 1000)
        assertEquals(2, deliveryService.saveBackgroundActivityInvokedCount)

        clock.setCurrentTime(startTime + 2000)
        service.onBackground(startTime + 2000)
        assertEquals(3, deliveryService.saveBackgroundActivityInvokedCount)
    }

    @Test
    fun `activity is cached on start capture when the service started in foreground`() {
        activityService.isInBackground = false
        this.service = createService()

        assertNull(deliveryService.lastSavedBackgroundActivity)

        service.onBackground(clock.now())

        assertNotNull(deliveryService.lastSavedBackgroundActivity)
    }

    @Test
    fun `calling save() persists the background activity in cache`() {
        activityService.isInBackground = false // start the service in foreground
        val startTime = clock.now()
        clock.setCurrentTime(startTime)

        this.service = createService()
        assertEquals(0, deliveryService.saveBackgroundActivityInvokedCount)

        // start capturing background activity 10 seconds later, activity is first cached
        service.onBackground(startTime + 10 * 1000)
        assertEquals(1, deliveryService.saveBackgroundActivityInvokedCount)

        // elapse another 10 seconds to get around the 5 seconds limitation
        clock.setCurrentTime(startTime + 20 * 1000)
        service.save()

        assertEquals(2, deliveryService.saveBackgroundActivityInvokedCount)
    }

    @Test
    fun `save() does not persist to disk if the activity was cached within the last 5 seconds`() {
        activityService.isInBackground = false // start the service in foreground

        this.service = createService()
        assertEquals(0, deliveryService.saveBackgroundActivityInvokedCount)

        service.onBackground(clock.now())
        assertEquals(1, deliveryService.saveBackgroundActivityInvokedCount)

        // save() will not persist to disk since the last time was less than 5 seconds ago
        service.save()
        assertEquals(1, deliveryService.saveBackgroundActivityInvokedCount)
    }

    @Test
    fun `NDK session id is updated when NDK is enabled`() {
        configService = FakeConfigService(
            autoDataCaptureBehavior = fakeAutoDataCaptureBehavior(
                localCfg = { LocalConfig("", true, SdkLocalConfig()) }
            )
        )
        this.service = createService()

        verify {
            ndkService.updateSessionId(checkNotNull(service.backgroundActivity).sessionId)
        }
    }

    @Test
    fun `NDK session id is not updated when NDK is not enabled`() {
        this.service = createService()

        verify(exactly = 0) {
            ndkService.updateSessionId(any())
        }
    }

    @Test
    fun `saving will persist the current completed spans but will not flush`() {
        service = createService()
        val now = TimeUnit.MILLISECONDS.toNanos(clock.now())
        spansService.initializeService(now, now + 10L)
        assertEquals(1, spansService.completedSpans()?.size)
        // move time ahead so the save will actually persist the new background activity message
        clock.tick(6000)
        service.save()
        assertNotNull(deliveryService.lastSavedBackgroundActivity)
        assertEquals(2, deliveryService.saveBackgroundActivityInvokedCount)
        assertEquals(1, deliveryService.lastSavedBackgroundActivity?.spans?.size)
        assertEquals(1, spansService.completedSpans()?.size)
    }

    @Test
    fun `crash will save and flush the current completed spans`() {
        // Prevent background thread from overwriting deliveryService.lastSavedBackgroundActivity
        blockableExecutorService.blockingMode = true
        service = createService()
        val now = TimeUnit.MILLISECONDS.toNanos(clock.now())
        spansService.initializeService(now, now + 10L)
        service.handleCrash("crashId")
        assertNotNull(deliveryService.lastSavedBackgroundActivity)

        // there should be 2 completed spans: session span and the sdk init span
        assertEquals(1, deliveryService.saveBackgroundActivityInvokedCount)
        assertEquals(2, deliveryService.lastSavedBackgroundActivity?.spans?.size)
        assertEquals(0, spansService.completedSpans()?.size)
    }

    @Test
    fun `foregrounding will flush the current completed spans`() {
        service = createService()
        val now = TimeUnit.MILLISECONDS.toNanos(clock.now())
        spansService.initializeService(now, now + 10L)
        service.onForeground(false, now, clock.now())
        assertNotNull(deliveryService.lastSavedBackgroundActivity)

        // there should be 2 completed spans: session span and the sdk init span
        assertEquals(2, deliveryService.lastSavedBackgroundActivity?.spans?.size)
        assertEquals(0, spansService.completedSpans()?.size)
    }

    @Test
    fun `sending background activity will flush the current completed spans`() {
        service = createService()
        val now = TimeUnit.MILLISECONDS.toNanos(clock.now())
        spansService.initializeService(now, now + 10L)
        service.sendBackgroundActivity()
        assertNotNull(deliveryService.lastSentBackgroundActivity)

        // there should be 2 completed spans: session span and the sdk init span
        assertEquals(2, deliveryService.lastSentBackgroundActivity?.spans?.size)
        assertEquals(0, spansService.completedSpans()?.size)
    }

    @Test
    fun `foregrounding background activity flushes breadcrumbs`() {
        service = createService()
        clock.tick(1000L)
        service.onForeground(false, 0, clock.now())
        assertNotNull(deliveryService.lastSavedBackgroundActivity)
        verify(exactly = 1) { mockBreadcrumbService.flushBreadcrumbs() }
    }

    @Test
    fun `saving background activity in the background will not flush breadcrumbs`() {
        service = createService()
        clock.tick(1000L)
        service.save()
        assertNotNull(deliveryService.lastSavedBackgroundActivity)
        verify(exactly = 0) { mockBreadcrumbService.flushBreadcrumbs() }
    }

    private fun createService(): EmbraceBackgroundActivityService {
        val collator = BackgroundActivityCollator(
            userService,
            preferencesService,
            eventService,
            remoteLogger,
            exceptionService,
            mockBreadcrumbService,
            metadataService,
            performanceInfoService,
            spansService,
            clock,
        )
        return EmbraceBackgroundActivityService(
            metadataService,
            activityService,
            deliveryService,
            configService,
            ndkService,
            clock,
            collator,
            lazy { blockableExecutorService }
        )
    }
}
