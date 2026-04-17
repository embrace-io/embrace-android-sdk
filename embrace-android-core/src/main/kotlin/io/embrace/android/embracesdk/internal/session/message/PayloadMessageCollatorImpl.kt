package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.envelope.session.SessionPartEnvelopeSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.store.Ordinal

/**
 * Generates a payload
 */
internal class PayloadMessageCollatorImpl(
    private val sessionPartEnvelopeSource: SessionPartEnvelopeSource,
    private val currentSessionPartSpan: CurrentSessionPartSpan,
) : PayloadMessageCollator {

    override fun buildInitialPart(params: InitialEnvelopeParams): SessionPartToken = with(params) {
        currentSessionPartSpan.readySession()
        SessionPartToken(
            sessionPartId = currentSessionPartSpan.getSessionId(),
            startTime = startTime,
            isColdStart = coldStart,
            appState = appState,
            startType = startType,
            sessionPartNumber = store.incrementAndGet(Ordinal.USER_SESSION_PART),
        )
    }

    override fun buildFinalEnvelope(params: FinalEnvelopeParams): Envelope<SessionPartPayload> {
        val envelope = sessionPartEnvelopeSource.getEnvelope(
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
