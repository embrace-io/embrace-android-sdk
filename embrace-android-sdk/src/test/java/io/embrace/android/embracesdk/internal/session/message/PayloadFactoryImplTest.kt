package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState.BACKGROUND
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState.FOREGROUND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PayloadFactoryImplTest {

    private lateinit var configService: FakeConfigService
    private lateinit var sessionPayloadSource: FakeSessionPayloadSource
    private lateinit var factory: PayloadFactoryImpl

    @Before
    fun setUp() {
        val initModule = FakeInitModule()
        configService = FakeConfigService()
        sessionPayloadSource = FakeSessionPayloadSource()
        val collator = PayloadMessageCollatorImpl(
            gatingService = FakeGatingService(),
            preferencesService = FakePreferenceService(),
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
            sessionEnvelopeSource = SessionEnvelopeSourceImpl(
                FakeEnvelopeMetadataSource(),
                FakeEnvelopeResourceSource(),
                sessionPayloadSource
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
    fun `verify expected payloads with ba enabled`() {
        configService.backgroundActivityCaptureEnabled = true
        verifyPayloadWithState(state = FOREGROUND, zygoteCreated = true, startNewSession = true)
        verifyPayloadWithState(state = BACKGROUND, zygoteCreated = true, startNewSession = true)
        verifyPayloadWithManual()
    }

    @Test
    fun `verify expected payloads with ba disabled`() {
        configService.backgroundActivityCaptureEnabled = false
        verifyPayloadWithState(state = FOREGROUND, zygoteCreated = true, startNewSession = false)
        verifyPayloadWithState(state = BACKGROUND, zygoteCreated = false, startNewSession = false)
        verifyPayloadWithManual()
    }

    private fun verifyPayloadWithState(state: ProcessState, zygoteCreated: Boolean, startNewSession: Boolean) {
        val zygote = factory.startPayloadWithState(state, 0, false)
        if (zygoteCreated) {
            assertTrue(checkNotNull(zygote).sessionId.isNotBlank())
            assertNotNull(factory.endPayloadWithState(state, 0, zygote))
            assertEquals(startNewSession, sessionPayloadSource.lastStartNewSession)
        } else {
            assertNull(zygote)
        }
    }

    private fun verifyPayloadWithManual() {
        val zygote = factory.startSessionWithManual(0)
        assertTrue(zygote.sessionId.isNotBlank())
        assertNotNull(factory.endSessionWithManual(0, zygote))
        assertEquals(true, sessionPayloadSource.lastStartNewSession)
    }
}
