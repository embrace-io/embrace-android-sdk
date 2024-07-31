package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState.BACKGROUND
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState.FOREGROUND
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PayloadFactoryImplTest {

    private lateinit var configService: FakeConfigService
    private lateinit var factory: PayloadFactoryImpl

    @Before
    fun setUp() {
        configService = FakeConfigService()
        val initModule = FakeInitModule()
        val collator = PayloadMessageCollatorImpl(
            gatingService = FakeGatingService(),
            preferencesService = FakePreferenceService(),
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
            sessionEnvelopeSource = SessionEnvelopeSourceImpl(
                FakeEnvelopeMetadataSource(),
                FakeEnvelopeResourceSource(),
                FakeSessionPayloadSource()
            )
        )
        factory = PayloadFactoryImpl(
            payloadMessageCollator = collator,
            configService = configService,
            logger = initModule.logger
        )
    }

    @Test
    fun `v2 payload generated`() {
        val session = checkNotNull(factory.startPayloadWithState(FOREGROUND, 0, false))
        checkNotNull(factory.endPayloadWithState(FOREGROUND, 0, session))
    }

    @Test
    fun `start payload with state generate payloads with valid session IDs if ba is enabled`() {
        configService.backgroundActivityCaptureEnabled = true
        assertTrue(checkNotNull(factory.startPayloadWithState(FOREGROUND, 0, false)).sessionId.isNotBlank())
        assertTrue(checkNotNull(factory.startPayloadWithState(BACKGROUND, 0, false)).sessionId.isNotBlank())
    }

    @Test
    fun `start payload with state generate payloads with valid session IDs only for foreground if ba is disabled`() {
        configService.backgroundActivityCaptureEnabled = false
        assertTrue(checkNotNull(factory.startPayloadWithState(FOREGROUND, 0, false)).sessionId.isNotBlank())
        assertNull(factory.startPayloadWithState(BACKGROUND, 0, false))
    }

    @Test
    fun `start payload with manual generate payloads with valid session IDs if ba is enabled`() {
        configService.backgroundActivityCaptureEnabled = true
        assertTrue(checkNotNull(factory.startSessionWithManual(100L)).sessionId.isNotBlank())
        assertTrue(checkNotNull(factory.startPayloadWithState(BACKGROUND, 0, false)).sessionId.isNotBlank())
    }

    @Test
    fun `start payload with manual generate payloads with valid session IDs if ba is disabled`() {
        configService.backgroundActivityCaptureEnabled = false
        assertTrue(checkNotNull(factory.startSessionWithManual(100L)).sessionId.isNotBlank())
    }
}
