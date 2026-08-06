package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeOtelPayloadMapper
import io.embrace.android.embracesdk.fakes.FakeProcessStateTracker
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeSessionPartTracker
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.createBackgroundActivityBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionPartToken
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartPayloadSourceImpl
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class PayloadFactoryBaTest {

    private val initial = fakeSessionPartToken()
    private lateinit var service: PayloadFactoryImpl
    private lateinit var clock: FakeClock
    private lateinit var metadataService: MetadataService
    private lateinit var sessionTracker: FakeSessionPartTracker
    private lateinit var activityService: FakeProcessStateTracker
    private lateinit var userService: UserService
    private lateinit var configService: FakeConfigService
    private lateinit var spanRepository: SpanRepository
    private lateinit var currentSessionPartSpan: CurrentSessionPartSpan
    private lateinit var spanService: SpanService
    private lateinit var blockingExecutorService: BlockingScheduledExecutorService

    @Before
    fun init() {
        clock = FakeClock(10000L)
        metadataService = FakeMetadataService()
        sessionTracker = FakeSessionPartTracker()
        activityService = FakeProcessStateTracker(ProcessState.BACKGROUND)
        userService = FakeUserService()
        val initModule = FakeInitModule(clock = clock)
        spanRepository = initModule.openTelemetryModule.spanRepository
        currentSessionPartSpan = initModule.openTelemetryModule.currentSessionPartSpan
        spanService = initModule.openTelemetryModule.spanService
        configService = FakeConfigService(
            backgroundActivityBehavior = createBackgroundActivityBehavior(
                remoteCfg = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 100f)),
            ),
        )
        blockingExecutorService = BlockingScheduledExecutorService(blockingMode = false)
    }

    @Test
    fun `crash will save and flush the current completed spans`() {
        // Prevent background thread from overwriting deliveryService.lastSavedBackgroundActivity
        blockingExecutorService = BlockingScheduledExecutorService(blockingMode = true)
        service = createService()
        val now = clock.now()
        spanService.initializeService(now)
        val msg = service.endPayloadWithCrash(ProcessState.BACKGROUND, now, initial, "crashId")

        // there should be 1 completed span: the session part span
        checkNotNull(msg)
        assertEquals(1, msg.data.spans?.size)
        assertEquals(0, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `foregrounding will flush the current completed spans`() {
        service = createService()
        spanService.initializeService(clock.now())
        val msg = service.endPayloadWithState(ProcessState.BACKGROUND, clock.now(), initial)

        // there should be 1 completed span: the session part span
        checkNotNull(msg)
        assertEquals(1, msg.data.spans?.size)
        assertEquals(0, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `sending background activity will flush the current completed spans`() {
        service = createService()
        spanService.initializeService(clock.now())
        clock.tick(1000L)
        val msg = service.endPayloadWithState(ProcessState.BACKGROUND, clock.now(), initial)

        // there should be 1 completed span: the session part span
        checkNotNull(msg)
        assertEquals(1, msg.data.spans?.size)
        assertEquals(0, spanRepository.completedOtelSpans().size)
    }

    private fun createService(createInitialSession: Boolean = true): PayloadFactoryImpl {
        val logger = InternalLoggerImpl()
        val payloadSourceModule = FakePayloadSourceModule(
            partPayloadSource = SessionPartPayloadSourceImpl(
                null,
                currentSessionPartSpan,
                spanRepository,
                FakeOtelPayloadMapper(),
                FakeProcessStateTracker(),
                FakeClock(),
                logger,
            ),
        )
        val collator = PayloadMessageCollatorImpl(
            payloadSourceModule.sessionPartEnvelopeSource,
            currentSessionPartSpan,
            FakeSessionIdsProvider(),
        )
        return PayloadFactoryImpl(collator, payloadSourceModule.logEnvelopeSource, configService, logger).apply {
            if (createInitialSession) {
                startPayloadWithState(ProcessState.BACKGROUND, clock.now(), true, { 1 }, { 1 })
            }
        }
    }
}
