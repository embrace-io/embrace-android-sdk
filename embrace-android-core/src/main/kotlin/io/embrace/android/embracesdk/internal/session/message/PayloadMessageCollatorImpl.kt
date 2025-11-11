package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.store.OrdinalStore

/**
 * Generates a payload
 */
internal class PayloadMessageCollatorImpl(
    private val sessionEnvelopeSource: SessionEnvelopeSource,
    private val store: OrdinalStore,
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
            number = getSessionNumber(store)
        )
    }

    override fun buildFinalEnvelope(params: FinalEnvelopeParams): Envelope<SessionPayload> {
        val envelope = sessionEnvelopeSource.getEnvelope(
            endType = params.endType,
            startNewSession = params.startNewSession,
            crashId = params.crashId
        )
        return Envelope(
            resource = envelope.resource,
            metadata = envelope.metadata,
            data = envelope.data,
            version = envelope.version,
            type = envelope.type
        )
    }
}
