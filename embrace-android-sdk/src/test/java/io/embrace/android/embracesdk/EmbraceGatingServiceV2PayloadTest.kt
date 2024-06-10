package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.fakes.fakeV1SessionMessage
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.opentelemetry.embCrashId
import io.embrace.android.embracesdk.payload.SessionMessage
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
    private lateinit var logMessageService: FakeLogMessageService
    private lateinit var sessionBehavior: SessionBehavior
    private var cfg: RemoteConfig? = RemoteConfig()

    @Before
    fun setUp() {
        sessionBehavior = fakeSessionBehavior { cfg }
        configService = FakeConfigService(sessionBehavior = fakeSessionBehavior { cfg })
        logMessageService = FakeLogMessageService()
        gatingService = EmbraceGatingService(configService, logMessageService, EmbLoggerImpl())
    }

    @Test
    fun `sessions are not gated by default`() {
        val result = gatingService.gateSessionEnvelope(fakeV1SessionMessage(), envelope)
        assertNotNull(checkNotNull(result.metadata).personas)
    }

    @Test
    fun `sessions with error log are not gated`() {
        cfg = buildCustomRemoteConfig(
            setOf(),
            setOf(SessionGatingKeys.FULL_SESSION_ERROR_LOGS)
        )
        logMessageService.errorLogIds = listOf("id1")
        val sessionMessage = SessionMessage(fakeSession())
        // result shouldn't be sanitized.
        val result = gatingService.gateSessionEnvelope(sessionMessage, envelope)
        assertNotNull(checkNotNull(result.metadata).personas)
    }

    @Test
    fun `sessions with crash are not gated`() {
        cfg = buildCustomRemoteConfig(
            setOf(),
            setOf(SessionGatingKeys.FULL_SESSION_ERROR_LOGS)
        )
        val sessionMessage = SessionMessage(
            data = SessionPayload(
                spans = listOf(
                    Span(
                        attributes = listOf(
                            Attribute("emb.type", "ux.session"),
                            Attribute(embCrashId.name, "my-crash-id")
                        )
                    )
                )
            ),
            metadata = EnvelopeMetadata(
                personas = setOf("foo")
            ),
            session = fakeSession()
        )

        // result shouldn't be sanitized.
        val result = gatingService.gateSessionEnvelope(sessionMessage, envelope)
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
