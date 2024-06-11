package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.payload.SessionMessage
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

    override fun buildFinalSessionMessage(params: FinalEnvelopeParams): SessionMessage {
        val envelope = gatingService.gateSessionEnvelope(
            params.crashId != null,
            sessionEnvelopeSource.getEnvelope(params.endType, params.crashId)
        )
        return SessionMessage(
            // future work: make legacy fields null here.
            resource = envelope.resource,
            metadata = envelope.metadata,
            data = envelope.data,
            newVersion = envelope.version,
            type = envelope.type
        )
    }
}
