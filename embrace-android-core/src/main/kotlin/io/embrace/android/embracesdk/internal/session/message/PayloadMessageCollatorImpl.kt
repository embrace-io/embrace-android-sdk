package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.gating.GatingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan

/**
 * Generates a V2 payload
 */
internal class PayloadMessageCollatorImpl(
    private val gatingService: GatingService,
    private val sessionEnvelopeSource: SessionEnvelopeSource,
    private val preferencesService: PreferencesService,
    private val currentSessionSpan: CurrentSessionSpan,
) : PayloadMessageCollator {

    override fun buildInitialSession(params: InitialEnvelopeParams): SessionZygote = with(params) {
        currentSessionSpan.readySession()
        SessionZygote(
            sessionId = currentSessionSpan.getSessionId(),
            startTime = startTime,
            isColdStart = coldStart,
            appState = appState,
            startType = startType,
            number = getSessionNumber(preferencesService)
        )
    }

    override fun buildFinalEnvelope(params: FinalEnvelopeParams): Envelope<SessionPayload> {
        val envelope = gatingService.gateSessionEnvelope(
            hasCrash = params.crashId != null,
            envelope = sessionEnvelopeSource.getEnvelope(
                endType = params.endType,
                startNewSession = params.startNewSession,
                crashId = params.crashId
            )
        )
        return Envelope<SessionPayload>(
            // future work: make legacy fields null here.
            resource = envelope.resource,
            metadata = envelope.metadata,
            data = envelope.data,
            version = envelope.version,
            type = envelope.type
        )
    }
}
