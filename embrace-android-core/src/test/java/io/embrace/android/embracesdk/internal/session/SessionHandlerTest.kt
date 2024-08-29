package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeNdkService
import io.embrace.android.embracesdk.fakes.FakeOtelPayloadMapper
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionZygote
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.config.local.SessionLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.envelope.session.SessionPayloadSourceImpl
import io.embrace.android.embracesdk.internal.gating.EmbraceGatingService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.SessionZygote
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class SessionHandlerTest {

    companion object {
        private val clock = FakeClock()
        private const val NOW = 123L
        private var sessionNumber = 5
    }

    private val initial = fakeSessionZygote().copy(startTime = NOW)
    private val userService: FakeUserService = FakeUserService()

    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var metadataService: FakeMetadataService
    private lateinit var localConfig: LocalConfig
    private lateinit var remoteConfig: RemoteConfig
    private lateinit var sessionLocalConfig: SessionLocalConfig
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var gatingService: FakeGatingService
    private lateinit var configService: FakeConfigService
    private lateinit var ndkService: FakeNdkService
    private lateinit var memoryCleanerService: FakeMemoryCleanerService
    private lateinit var payloadFactory: PayloadFactory
    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var scheduledWorker: ScheduledWorker
    private lateinit var logger: EmbLogger
    private lateinit var spanRepository: SpanRepository
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var sessionPropertiesService: SessionPropertiesService

    @Before
    fun before() {
        executorService = BlockingScheduledExecutorService()
        scheduledWorker = ScheduledWorker(executorService)
        logger = EmbLoggerImpl()
        clock.setCurrentTime(NOW)
        sessionPropertiesService = FakeSessionPropertiesService()
        ndkService = FakeNdkService()
        metadataService = FakeMetadataService()
        sessionIdTracker = FakeSessionIdTracker()
        memoryCleanerService = FakeMemoryCleanerService()

        localConfig = LocalConfig(
            appId = "abcde",
            ndkEnabled = true,
            sdkConfig = SdkLocalConfig()
        )
        sessionLocalConfig = SessionLocalConfig()
        remoteConfig = RemoteConfig()
        configService = FakeConfigService(
            autoDataCaptureBehavior = fakeAutoDataCaptureBehavior(
                localCfg = { localConfig },
                remoteCfg = { remoteConfig }
            ),
            sessionBehavior = fakeSessionBehavior(
                localCfg = { sessionLocalConfig },
                remoteCfg = { remoteConfig }
            ),
            dataCaptureEventBehavior = fakeDataCaptureEventBehavior(
                remoteCfg = { remoteConfig }
            )
        )
        gatingService = FakeGatingService(EmbraceGatingService(configService, FakeLogService(), FakeEmbLogger()))
        preferencesService = FakePreferenceService()
        deliveryService = FakeDeliveryService()
        val initModule = FakeInitModule(clock = clock)
        spanSink = initModule.openTelemetryModule.spanSink
        spanService = initModule.openTelemetryModule.spanService
        spanRepository = initModule.openTelemetryModule.spanRepository
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan

        val collator = PayloadMessageCollatorImpl(
            gatingService,
            SessionEnvelopeSourceImpl(
                metadataSource = FakeEnvelopeMetadataSource(),
                resourceSource = FakeEnvelopeResourceSource(),
                sessionPayloadSource = SessionPayloadSourceImpl(
                    { null },
                    spanSink,
                    currentSessionSpan,
                    spanRepository,
                    FakeOtelPayloadMapper(),
                    logger
                )
            ),
            preferencesService,
            currentSessionSpan
        )
        payloadFactory = PayloadFactoryImpl(collator, configService, logger)
    }

    @Test
    fun `onSession started successfully with no preference service session number`() {
        // return absent session number
        sessionNumber = 0
        sessionLocalConfig = SessionLocalConfig()
        // this is needed so session handler creates automatic session stopper

        payloadFactory.startPayloadWithState(ProcessState.FOREGROUND, NOW, true)

        assertEquals(1, preferencesService.incrementAndGetSessionNumberCount)
    }

    @Test
    fun `endSession includes completed spans in message`() {
        startFakeSession()
        initializeServices()
        spanService.recordSpan("test-span") {
            // do nothing
        }
        clock.tick(30000)
        val msg = payloadFactory.endPayloadWithState(ProcessState.FOREGROUND, 10L, initial)
        assertSpanInSessionEnvelope(msg)
    }

    @Test
    fun `clearing user info disallowed for state sessions`() {
        startFakeSession()
        clock.tick(30000)
        payloadFactory.endPayloadWithState(ProcessState.FOREGROUND, 10L, initial)
        assertEquals(0, userService.clearedCount)
    }

    @Test
    fun `crashes includes completed spans in message`() {
        startFakeSession()
        initializeServices()
        spanService.recordSpan("test-span") {
            // do nothing
        }
        val msg = payloadFactory.endPayloadWithCrash(
            ProcessState.FOREGROUND,
            clock.now(),
            initial,
            "fakeCrashId"
        )
        assertSpanInSessionEnvelope(msg)
    }

    @Test
    fun `start session successfully`() {
        assertNotNull(startFakeSession())
    }

    @Test
    fun `backgrounding flushes completed spans`() {
        startFakeSession()
        initializeServices()
        spanService.recordSpan("test-span") {}
        assertEquals(1, spanSink.completedSpans().size)

        clock.tick(15000L)
        val envelope =
            checkNotNull(
                payloadFactory.endPayloadWithState(
                    ProcessState.FOREGROUND,
                    clock.now(),
                    initial
                )
            )
        val spans = checkNotNull(envelope.data.spans)
        assertEquals(2, spans.size)
        assertEquals(0, spanSink.completedSpans().size)
    }

    @Test
    fun `crash ending flushes completed spans`() {
        startFakeSession()
        initializeServices()
        spanService.recordSpan("test-span") {}
        assertEquals(1, spanSink.completedSpans().size)

        payloadFactory.endPayloadWithCrash(ProcessState.FOREGROUND, clock.now(), initial, "crashId")
        assertEquals(0, spanSink.completedSpans().size)
    }

    private fun startFakeSession(): SessionZygote {
        return checkNotNull(payloadFactory.startPayloadWithState(ProcessState.FOREGROUND, NOW, true))
    }

    private fun initializeServices(startTimeMillis: Long = clock.now()) {
        spanService.initializeService(startTimeMillis)
    }

    private fun assertSpanInSessionEnvelope(envelope: Envelope<SessionPayload>?) {
        assertNotNull(envelope)
        val spans = checkNotNull(envelope?.data?.spans)
        assertEquals(2, spans.size)
        val expectedSpans = listOf("emb-test-span", "emb-session")
        assertEquals(expectedSpans, spans.map(Span::name))
    }
}
