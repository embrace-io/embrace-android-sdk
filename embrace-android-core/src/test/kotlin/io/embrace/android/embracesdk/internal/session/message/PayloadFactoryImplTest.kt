package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeSessionPartPayloadSource
import io.embrace.android.embracesdk.fakes.createBackgroundActivityBehavior
import io.embrace.android.embracesdk.fakes.createPersistenceBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessState.BACKGROUND
import io.embrace.android.embracesdk.internal.arch.state.ProcessState.FOREGROUND
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PayloadFactoryImplTest {

    private lateinit var configService: FakeConfigService
    private lateinit var partPayloadSource: FakeSessionPartPayloadSource
    private lateinit var factory: PayloadFactoryImpl

    @Before
    fun setUp() {
        val initModule = FakeInitModule()
        configService = FakeConfigService()
        partPayloadSource = FakeSessionPartPayloadSource()
        val payloadSourceModule = FakePayloadSourceModule(
            partPayloadSource = partPayloadSource,
        )
        val collator = PayloadMessageCollatorImpl(
            sessionPartEnvelopeSource = payloadSourceModule.sessionPartEnvelopeSource,
            currentSessionPartSpan = initModule.openTelemetryModule.currentSessionPartSpan,
            sessionIdsProvider = FakeSessionIdsProvider(),
        )
        factory = PayloadFactoryImpl(
            payloadMessageCollator = collator,
            logEnvelopeSource = payloadSourceModule.logEnvelopeSource,
            configService = configService,
            logger = initModule.logger,
        )
    }

    @Test
    fun `payload generated`() {
        val session = checkNotNull(factory.startPayloadWithState(FOREGROUND, 0, false, { 1 }, { 1 }))
        checkNotNull(factory.endPayloadWithState(FOREGROUND, 0, session))
    }

    @Test
    fun `verify expected payloads with ba enabled`() {
        configService.backgroundActivityBehavior = createBackgroundActivityBehavior(
            remoteCfg = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 100f)),
        )
        verifyPayloadWithState(state = FOREGROUND, zygoteCreated = true, startNewSession = true)
        verifyPayloadWithState(state = BACKGROUND, zygoteCreated = true, startNewSession = true)
        verifyPayloadWithManual()
    }

    @Test
    fun `verify expected payloads with ba disabled`() {
        configService.backgroundActivityBehavior = createBackgroundActivityBehavior(
            remoteCfg = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 0f)),
        )
        verifyPayloadWithState(state = FOREGROUND, zygoteCreated = true, startNewSession = false)
        verifyPayloadWithState(state = BACKGROUND, zygoteCreated = false, startNewSession = false)
        verifyPayloadWithManual()
    }

    @Test
    fun `no envelope is built when multi file persistence is enabled`() {
        configService.persistenceBehavior = createPersistenceBehavior(
            remoteCfg = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f),
        )
        assertNull(factory.endPayloadWithState(FOREGROUND, 0, newSessionPart()))
        assertNull(factory.endPayloadWithCrash(FOREGROUND, 0, newSessionPart(), "crashId"))
        assertNull(factory.endSessionWithManual(0, newSessionPart()))
        assertEquals(0, partPayloadSource.payloadBuiltCount)
        assertEquals(3, partPayloadSource.endedWithoutPayloadCount)
    }

    @Test
    fun `an envelope is built when multi file persistence is disabled`() {
        assertNotNull(factory.endPayloadWithState(FOREGROUND, 0, newSessionPart()))
        assertNotNull(factory.endPayloadWithCrash(FOREGROUND, 0, newSessionPart(), "crashId"))
        assertNotNull(factory.endSessionWithManual(0, newSessionPart()))
        assertEquals(3, partPayloadSource.payloadBuiltCount)
        assertEquals(0, partPayloadSource.endedWithoutPayloadCount)
    }

    @Test
    fun `a periodic cache snapshot still builds an envelope when multi file persistence is enabled`() {
        configService.persistenceBehavior = createPersistenceBehavior(
            remoteCfg = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f),
        )
        assertNotNull(factory.snapshotPayload(FOREGROUND, 0, newSessionPart()))
        assertEquals(1, partPayloadSource.payloadBuiltCount)
    }

    private fun newSessionPart() = checkNotNull(factory.startPayloadWithState(FOREGROUND, 0, false, { 1 }, { 1 }))

    private fun verifyPayloadWithState(state: ProcessState, zygoteCreated: Boolean, startNewSession: Boolean) {
        val zygote = factory.startPayloadWithState(state, 0, false, { 1 }, { 1 })
        if (zygoteCreated) {
            assertTrue(checkNotNull(zygote).sessionPartId.isNotBlank())
            assertNotNull(factory.endPayloadWithState(state, 0, zygote))
            assertEquals(startNewSession, partPayloadSource.lastStartNewSession)
        } else {
            assertNull(zygote)
        }
    }

    private fun verifyPayloadWithManual() {
        val zygote = checkNotNull(factory.startSessionWithManual(FOREGROUND, 0, { 1 }, { 1 }))
        assertTrue(zygote.sessionPartId.isNotBlank())
        assertNotNull(factory.endSessionWithManual(0, zygote))
        assertEquals(true, partPayloadSource.lastStartNewSession)
    }
}
