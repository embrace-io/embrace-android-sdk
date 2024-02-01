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
import io.embrace.android.embracesdk.fakes.FakeThermalStatusService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.internal.spans.SpansSink
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.session.message.PayloadMessageCollator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class PayloadFactoryBaTest {

    private val initial = fakeBackgroundActivity()
    private lateinit var service: PayloadFactoryImpl
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
    private lateinit var spansSink: SpansSink
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var spansService: SpansService
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
        val initModule = FakeInitModule(clock = clock)
        spansSink = initModule.spansSink
        currentSessionSpan = initModule.currentSessionSpan
        spansService = initModule.spansService
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
    fun `background activity is not started whn the service initializes in the foreground`() {
        activityService.isInBackground = false
        this.service = createService(false)
        assertTrue(deliveryService.lastSavedBackgroundActivities.isEmpty())
    }

    @Test
    fun `crash will save and flush the current completed spans`() {
        // Prevent background thread from overwriting deliveryService.lastSavedBackgroundActivity
        blockingExecutorService = BlockingScheduledExecutorService(blockingMode = true)
        service = createService()
        val now = TimeUnit.MILLISECONDS.toNanos(clock.now())
        spansService.initializeService(now)
        val msg = service.endBackgroundActivityWithCrash(initial, now, "crashId")

        // there should be 1 completed span: the session span
        assertEquals(1, msg.spans?.size)
        assertEquals(0, spansSink.completedSpans().size)
    }

    @Test
    fun `foregrounding will flush the current completed spans`() {
        service = createService()
        spansService.initializeService(TimeUnit.MILLISECONDS.toNanos(clock.now()))
        val msg = service.endBackgroundActivityWithState(initial, clock.now())

        // there should be 1 completed span: the session span
        assertEquals(1, msg.spans?.size)
        assertEquals(0, spansSink.completedSpans().size)
    }

    @Test
    fun `sending background activity will flush the current completed spans`() {
        service = createService()
        spansService.initializeService(TimeUnit.MILLISECONDS.toNanos(clock.now()))
        clock.tick(1000L)
        val msg = service.endBackgroundActivityWithState(initial, clock.now())

        // there should be 1 completed span: the session span
        assertEquals(1, msg.spans?.size)
        assertEquals(0, spansSink.completedSpans().size)
    }

    @Test
    fun `foregrounding background activity flushes breadcrumbs`() {
        service = createService()
        clock.tick(1000L)
        service.endBackgroundActivityWithState(initial, clock.now())
        assertEquals(1, breadcrumbService.flushCount)
    }

    private fun createService(createInitialSession: Boolean = true): PayloadFactoryImpl {
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
            spansSink,
            currentSessionSpan,
            FakeSessionPropertiesService(),
            FakeStartupService()
        )
        return PayloadFactoryImpl(collator).apply {
            if (createInitialSession) {
                startBackgroundActivityWithState(clock.now(), true)
            }
        }
    }
}
