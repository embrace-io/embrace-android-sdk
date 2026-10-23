package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeProcessStateTracker
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeSessionPartEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakeSessionPartTracker
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.logging.ErrorHandlerImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class PayloadFactorySessionPartTest(
    private val otelSdkMode: OtelSdkMode,
) {

    private lateinit var service: PayloadFactory
    private lateinit var configService: FakeConfigService
    private lateinit var clock: FakeClock
    private lateinit var metadataService: MetadataService
    private lateinit var sessionTracker: FakeSessionPartTracker
    private lateinit var activityService: FakeProcessStateTracker
    private lateinit var userService: UserService
    private lateinit var spanRepository: SpanRepository
    private lateinit var currentSessionPartSpan: CurrentSessionPartSpan
    private lateinit var spanService: SpanService
    private lateinit var blockingExecutorService: BlockingScheduledExecutorService

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()

        private val appStateTracker = FakeProcessStateTracker()
    }

    @Before
    fun before() {
        configService = FakeConfigService()
        clock = FakeClock(10000L)

        metadataService = FakeMetadataService()
        sessionTracker = FakeSessionPartTracker()
        activityService = FakeProcessStateTracker(ProcessState.BACKGROUND)
        userService = FakeUserService()
        val initServices = TestInitServices(clock = clock, otelSdkMode = otelSdkMode)
        spanRepository = initServices.otelStack.spanRepository
        currentSessionPartSpan = initServices.otelStack.currentSessionPartSpan
        spanService = initServices.otelStack.spanService
        blockingExecutorService = BlockingScheduledExecutorService(blockingMode = false)
    }

    @Test
    fun `spanService that is not initialized will not result in any complete spans`() {
        initializeSessionService()
        assertEquals(0, spanRepository.completedOtelSpans().size)
    }

    private fun initializeSessionService() {
        appStateTracker.state = ProcessState.BACKGROUND

        val errorHandler = ErrorHandlerImpl()
        val collator = PayloadMessageCollatorImpl(
            FakeSessionPartEnvelopeSource(),
            currentSessionPartSpan,
            FakeSessionIdsProvider(),
        )
        service = PayloadFactoryImpl(collator, FakeLogEnvelopeSource(), FakeConfigService(), errorHandler)
    }
}
