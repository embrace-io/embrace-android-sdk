package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeAppStateTracker
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeOrdinalStore
import io.embrace.android.embracesdk.fakes.FakeSessionTracker
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.mockk.clearAllMocks
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ExecutorService

internal class PayloadFactorySessionTest {

    private lateinit var spanSink: SpanSink
    private lateinit var service: PayloadFactory
    private lateinit var configService: FakeConfigService
    private lateinit var clock: FakeClock
    private lateinit var metadataService: MetadataService
    private lateinit var sessionTracker: FakeSessionTracker
    private lateinit var activityService: FakeAppStateTracker
    private lateinit var userService: UserService
    private lateinit var spanRepository: SpanRepository
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var spanService: SpanService
    private lateinit var store: FakeOrdinalStore
    private lateinit var blockingExecutorService: BlockingScheduledExecutorService

    companion object {

        private val appStateTracker = FakeAppStateTracker()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(ExecutorService::class)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    @Before
    fun before() {
        configService = FakeConfigService()
        clock = FakeClock(10000L)
        spanSink = FakeInitModule(clock = clock).openTelemetryModule.spanSink

        metadataService = FakeMetadataService()
        sessionTracker = FakeSessionTracker()
        activityService = FakeAppStateTracker(AppState.BACKGROUND)
        store = FakeOrdinalStore()
        userService = FakeUserService()
        val initModule = FakeInitModule(clock = clock)
        spanRepository = initModule.openTelemetryModule.spanRepository
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan
        spanService = initModule.openTelemetryModule.spanService
        blockingExecutorService = BlockingScheduledExecutorService(blockingMode = false)
    }

    @After
    fun after() {
        clearAllMocks(answers = false)
    }

    @Test
    fun `spanService that is not initialized will not result in any complete spans`() {
        initializeSessionService()
        assertEquals(0, spanSink.completedSpans().size)
    }

    private fun initializeSessionService() {
        appStateTracker.state = AppState.BACKGROUND

        val payloadSourceModule = FakePayloadSourceModule()
        val logger = EmbLoggerImpl()
        val collator = PayloadMessageCollatorImpl(
            payloadSourceModule.sessionEnvelopeSource,
            store,
            currentSessionSpan
        )
        service = PayloadFactoryImpl(collator, payloadSourceModule.logEnvelopeSource, FakeConfigService(), logger)
    }
}
