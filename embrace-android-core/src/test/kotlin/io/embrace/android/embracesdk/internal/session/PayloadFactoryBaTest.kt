package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeOtelPayloadMapper
import io.embrace.android.embracesdk.fakes.FakeProcessStateTracker
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeSessionPartEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakeSessionPartTracker
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.fakeSessionPartToken
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.config.resolved.BackgroundActivityConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartPayloadSourceImpl
import io.embrace.android.embracesdk.internal.logging.ErrorHandlerImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class PayloadFactoryBaTest(
    private val otelSdkMode: OtelSdkMode,
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }

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
    private lateinit var blockingExecutorService: BlockingScheduledExecutorService

    @Before
    fun init() {
        clock = FakeClock(10000L)
        metadataService = FakeMetadataService()
        sessionTracker = FakeSessionPartTracker()
        activityService = FakeProcessStateTracker(ProcessState.BACKGROUND)
        userService = FakeUserService()
        val initServices = TestInitServices(clock = clock, otelSdkMode = otelSdkMode)
        spanRepository = initServices.otelStack.spanRepository
        currentSessionPartSpan = initServices.otelStack.currentSessionPartSpan
        configService = FakeConfigService(
            config = EmbraceConfig(
                backgroundActivity = { BackgroundActivityConfig(captureEnabled = { true }) },
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
        currentSessionPartSpan.initializeService(now)
        val msg = service.endPayloadWithCrash(ProcessState.BACKGROUND, now, initial, "crashId")

        // there should be 1 completed span: the session part span
        checkNotNull(msg)
        assertEquals(1, msg.data.spans?.size)
        assertEquals(0, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `foregrounding will flush the current completed spans`() {
        service = createService()
        currentSessionPartSpan.initializeService(clock.now())
        val msg = service.endPayloadWithState(ProcessState.BACKGROUND, clock.now(), initial)

        // there should be 1 completed span: the session part span
        checkNotNull(msg)
        assertEquals(1, msg.data.spans?.size)
        assertEquals(0, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `sending background activity will flush the current completed spans`() {
        service = createService()
        currentSessionPartSpan.initializeService(clock.now())
        clock.tick(1000L)
        val msg = service.endPayloadWithState(ProcessState.BACKGROUND, clock.now(), initial)

        // there should be 1 completed span: the session part span
        checkNotNull(msg)
        assertEquals(1, msg.data.spans?.size)
        assertEquals(0, spanRepository.completedOtelSpans().size)
    }

    private fun createService(createInitialSession: Boolean = true): PayloadFactoryImpl {
        val errorHandler = ErrorHandlerImpl()
        val sessionPartEnvelopeSource = FakeSessionPartEnvelopeSource(
            partPayloadSource = SessionPartPayloadSourceImpl(
                null,
                currentSessionPartSpan,
                spanRepository,
                FakeOtelPayloadMapper(),
                FakeProcessStateTracker(),
                FakeClock(),
                errorHandler,
            ),
        )
        val collator = PayloadMessageCollatorImpl(
            sessionPartEnvelopeSource,
            currentSessionPartSpan,
            FakeSessionIdsProvider(),
        )
        return PayloadFactoryImpl(collator, FakeLogEnvelopeSource(), configService, errorHandler).apply {
            if (createInitialSession) {
                startPayloadWithState(ProcessState.BACKGROUND, clock.now(), true, { 1 }, { 1 })
            }
        }
    }
}
