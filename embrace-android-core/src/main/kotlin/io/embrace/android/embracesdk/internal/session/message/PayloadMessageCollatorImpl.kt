package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.envelope.session.SessionPartEnvelopeSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.id.SessionIdProvider
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan

/**
 * Generates a payload
 */
internal class PayloadMessageCollatorImpl(
    private val sessionPartEnvelopeSource: SessionPartEnvelopeSource,
    private val currentSessionPartSpan: CurrentSessionPartSpan,
    private val sessionIdProvider: SessionIdProvider,
) : PayloadMessageCollator {

    override fun buildInitialPart(params: InitialEnvelopeParams): SessionPartToken = with(params) {
        currentSessionPartSpan.readySession()
        SessionPartToken(
            sessionPartId = currentSessionPartSpan.getSessionId(),
            userSessionId = sessionIdProvider.getCurrentUserSessionId(),
            startTime = startTime,
            appState = appState,
            isColdStart = coldStart,
            startType = startType,
            sessionPartNumber = partNumber,
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
