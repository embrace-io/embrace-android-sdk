package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeNdkService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.mockk.clearAllMocks
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ExecutorService

internal class PayloadFactorySessionTest {

    private lateinit var spanSink: SpanSink
    private lateinit var service: PayloadFactory
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var configService: FakeConfigService
    private lateinit var clock: FakeClock
    private lateinit var metadataService: MetadataService
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var activityService: FakeProcessStateService
    private lateinit var userService: UserService
    private lateinit var ndkService: FakeNdkService
    private lateinit var spanRepository: SpanRepository
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var spanService: SpanService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var blockingExecutorService: BlockingScheduledExecutorService

    companion object {

        private val processStateService = FakeProcessStateService()

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
        deliveryService = FakeDeliveryService()
        configService = FakeConfigService()
        clock = FakeClock(10000L)
        spanSink = FakeInitModule(clock = clock).openTelemetryModule.spanSink

        metadataService = FakeMetadataService()
        sessionIdTracker = FakeSessionIdTracker()
        activityService = FakeProcessStateService(isInBackground = true)
        ndkService = FakeNdkService()
        preferencesService = FakePreferenceService()
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
    fun `initializing service should detect early sessions`() {
        initializeSessionService(isActivityInBackground = false)
        assertNull(deliveryService.lastSentCachedSession)
    }

    @Test
    fun `on foreground starts state session successfully for cold start`() {
        initializeSessionService()
        val coldStart = true

        service.startPayloadWithState(ProcessState.FOREGROUND, 456, coldStart)
        assertNull(deliveryService.lastSentCachedSession)
    }

    @Test
    fun `spanService that is not initialized will not result in any complete spans`() {
        initializeSessionService()
        assertEquals(0, spanSink.completedSpans().size)
    }

    private fun initializeSessionService(
        isActivityInBackground: Boolean = true,
    ) {
        processStateService.isInBackground = isActivityInBackground

        val sessionEnvelopeSource = SessionEnvelopeSourceImpl(
            metadataSource = FakeEnvelopeMetadataSource(),
            resourceSource = FakeEnvelopeResourceSource(),
            sessionPayloadSource = FakeSessionPayloadSource()
        )
        val logger = EmbLoggerImpl()
        val gatingService = FakeGatingService()
        val collator = PayloadMessageCollatorImpl(
            gatingService,
            sessionEnvelopeSource,
            preferencesService,
            currentSessionSpan
        )
        service = PayloadFactoryImpl(collator, FakeConfigService(), logger)
    }
}
