package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.session.message.PayloadFactory
import io.embrace.android.embracesdk.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.session.message.V1PayloadMessageCollator
import io.embrace.android.embracesdk.session.message.V2PayloadMessageCollator
import io.mockk.clearAllMocks
import io.mockk.mockk
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

    companion object {

        private val processStateService = FakeProcessStateService()
        private val clock = FakeClock()

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
        spanSink = FakeInitModule(clock = clock).openTelemetryModule.spanSink
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
        isActivityInBackground: Boolean = true
    ) {
        processStateService.isInBackground = isActivityInBackground

        val sessionEnvelopeSource = SessionEnvelopeSourceImpl(
            metadataSource = FakeEnvelopeMetadataSource(),
            resourceSource = FakeEnvelopeResourceSource(),
            sessionPayloadSource = FakeSessionPayloadSource()
        )
        val logger = EmbLoggerImpl()
        val v1Collator = mockk<V1PayloadMessageCollator>(relaxed = true)
        val v2Collator = V2PayloadMessageCollator(FakeGatingService(), v1Collator, sessionEnvelopeSource, logger)
        service = PayloadFactoryImpl(v1Collator, v2Collator, FakeConfigService(), logger)
    }
}
