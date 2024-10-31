package io.embrace.android.embracesdk.internal.event.gating

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.createSessionBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.gating.EmbraceGatingService
import io.embrace.android.embracesdk.internal.gating.SessionGatingKeys
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class EmbraceGatingServiceV2PayloadTest {

    private val envelope = Envelope(
        data = SessionPayload(),
        metadata = EnvelopeMetadata(
            personas = setOf("persona1", "persona2")
        )
    )

    private lateinit var gatingService: EmbraceGatingService
    private lateinit var configService: FakeConfigService
    private lateinit var logService: FakeLogService
    private lateinit var sessionBehavior: SessionBehavior
    private var cfg: RemoteConfig? = RemoteConfig()

    @Before
    fun setUp() {
        sessionBehavior = createSessionBehavior { cfg }
        configService = FakeConfigService(sessionBehavior = createSessionBehavior { cfg })
        logService = FakeLogService()
        gatingService = EmbraceGatingService(configService, logService)
    }

    @Test
    fun `sessions are not gated by default`() {
        val result = gatingService.gateSessionEnvelope(false, envelope)
        assertNotNull(checkNotNull(result.metadata).personas)
    }

    @Test
    fun `sessions with error log are not gated`() {
        cfg = buildCustomRemoteConfig(
            setOf(),
            setOf(SessionGatingKeys.FULL_SESSION_ERROR_LOGS)
        )
        logService.errorLogIds = listOf("id1")
        // result shouldn't be sanitized.
        val result = gatingService.gateSessionEnvelope(false, envelope)
        assertNotNull(checkNotNull(result.metadata).personas)
    }

    @Test
    fun `sessions with crash are not gated`() {
        cfg = buildCustomRemoteConfig(
            setOf(),
            setOf(SessionGatingKeys.FULL_SESSION_ERROR_LOGS)
        )

        // result shouldn't be sanitized.
        val result = gatingService.gateSessionEnvelope(true, envelope)
        assertNotNull(checkNotNull(result.metadata).personas)
    }

    private fun buildCustomRemoteConfig(components: Set<String>?, fullSessionEvents: Set<String>?) =
        RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                isEnabled = true,
                sessionComponents = components,
                fullSessionEvents = fullSessionEvents
            )
        )
}
