package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.payload.SessionZygote
import io.embrace.android.embracesdk.prefs.PreferencesService

/**
 * Generates a V2 payload
 */
internal class PayloadMessageCollatorImpl(
    private val gatingService: GatingService,
    private val sessionEnvelopeSource: SessionEnvelopeSource,
    private val preferencesService: PreferencesService,
    private val currentSessionSpan: CurrentSessionSpan
) : PayloadMessageCollator {

    override fun buildInitialSession(params: InitialEnvelopeParams) = with(params) {
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
            params.crashId != null,
            sessionEnvelopeSource.getEnvelope(params.endType, params.crashId)
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
