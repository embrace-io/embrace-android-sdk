package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.anr.AnrOtelMapper
import io.embrace.android.embracesdk.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.LocalConfigParser
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.fakeBackgroundActivity
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePerformanceInfoService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.session.message.V1PayloadMessageCollator
import io.embrace.android.embracesdk.session.message.V2PayloadMessageCollator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PayloadFactoryBaTest {

    private val initial = fakeBackgroundActivity()
    private lateinit var service: PayloadFactoryImpl
    private lateinit var clock: FakeClock
    private lateinit var performanceInfoService: FakePerformanceInfoService
    private lateinit var metadataService: MetadataService
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var activityService: FakeProcessStateService
    private lateinit var eventService: EventService
    private lateinit var logMessageService: LogMessageService
    private lateinit var userService: UserService
    private lateinit var internalErrorService: InternalErrorService
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var ndkService: FakeNdkService
    private lateinit var configService: FakeConfigService
    private lateinit var localConfig: LocalConfig
    private lateinit var spanRepository: SpanRepository
    private lateinit var spanSink: SpanSink
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var spanService: SpanService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var blockingExecutorService: BlockingScheduledExecutorService

    @Before
    fun init() {
        clock = FakeClock(10000L)
        performanceInfoService = FakePerformanceInfoService()
        metadataService = FakeMetadataService()
        sessionIdTracker = FakeSessionIdTracker()
        activityService = FakeProcessStateService(isInBackground = true)
        eventService = FakeEventService()
        logMessageService = FakeLogMessageService()
        internalErrorService = FakeInternalErrorService()
        deliveryService = FakeDeliveryService()
        ndkService = FakeNdkService()
        preferencesService = FakePreferenceService(backgroundActivityEnabled = true)
        userService = FakeUserService()
        val initModule = FakeInitModule(clock = clock)
        spanRepository = initModule.openTelemetryModule.spanRepository
        spanSink = initModule.openTelemetryModule.spanSink
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan
        spanService = initModule.openTelemetryModule.spanService
        configService = FakeConfigService(
            backgroundActivityCaptureEnabled = true
        )
        configService.updateListeners()
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"background_activity\": {\"max_background_activity_seconds\": 3600}}",
            EmbraceSerializer(),
            EmbLoggerImpl()
        )

        blockingExecutorService = BlockingScheduledExecutorService(blockingMode = false)
    }

    @Test
    fun `background activity is not started whn the service initializes in the foreground`() {
        activityService.isInBackground = false
        this.service = createService(false)
        assertTrue(deliveryService.savedSessionMessages.isEmpty())
    }

    @Test
    fun `crash will save and flush the current completed spans`() {
        // Prevent background thread from overwriting deliveryService.lastSavedBackgroundActivity
        blockingExecutorService = BlockingScheduledExecutorService(blockingMode = true)
        service = createService()
        val now = clock.now()
        spanService.initializeService(now)
        val msg = service.endPayloadWithCrash(ProcessState.BACKGROUND, now, initial, "crashId")

        // there should be 1 completed span: the session span
        checkNotNull(msg)
        assertEquals(1, msg.spans?.size)
        assertEquals(0, spanSink.completedSpans().size)
    }

    @Test
    fun `foregrounding will flush the current completed spans`() {
        service = createService()
        spanService.initializeService(clock.now())
        val msg = service.endPayloadWithState(ProcessState.BACKGROUND, clock.now(), initial)

        // there should be 1 completed span: the session span
        checkNotNull(msg)
        assertEquals(1, msg.spans?.size)
        assertEquals(0, spanSink.completedSpans().size)
    }

    @Test
    fun `sending background activity will flush the current completed spans`() {
        service = createService()
        spanService.initializeService(clock.now())
        clock.tick(1000L)
        val msg = service.endPayloadWithState(ProcessState.BACKGROUND, clock.now(), initial)

        // there should be 1 completed span: the session span
        checkNotNull(msg)
        assertEquals(1, msg.spans?.size)
        assertEquals(0, spanSink.completedSpans().size)
    }

    private fun createService(createInitialSession: Boolean = true): PayloadFactoryImpl {
        val gatingService = FakeGatingService()
        val logger = EmbLoggerImpl()
        val collator = V1PayloadMessageCollator(
            gatingService,
            metadataService,
            eventService,
            logMessageService,
            internalErrorService,
            performanceInfoService,
            FakeWebViewService(),
            null,
            userService,
            preferencesService,
            spanRepository,
            spanSink,
            currentSessionSpan,
            FakeSessionPropertiesService(),
            FakeStartupService(),
            AnrOtelMapper(FakeAnrService()),
            NativeAnrOtelMapper(null, EmbraceSerializer()),
            logger
        )
        val sessionEnvelopeSource = SessionEnvelopeSourceImpl(
            metadataSource = FakeEnvelopeMetadataSource(),
            resourceSource = FakeEnvelopeResourceSource(),
            sessionPayloadSource = FakeSessionPayloadSource()
        )
        val v2Collator = V2PayloadMessageCollator(gatingService, collator, sessionEnvelopeSource, logger)
        return PayloadFactoryImpl(collator, v2Collator, configService, logger).apply {
            if (createInitialSession) {
                startPayloadWithState(ProcessState.BACKGROUND, clock.now(), true)
            }
        }
    }
}
