package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage

/**
 * Generates a V2 payload. This currently calls through to the V1 collator for
 * backwards compatibility.
 */
internal class V2PayloadMessageCollator(
    private val v1Collator: V1PayloadMessageCollator
) : PayloadMessageCollator {

    override fun buildInitialSession(params: InitialEnvelopeParams): Session {
        return v1Collator.buildInitialSession(params)
    }

    override fun buildFinalSessionMessage(params: FinalEnvelopeParams.SessionParams): SessionMessage {
        return v1Collator.buildFinalSessionMessage(params)
    }

    override fun buildFinalBackgroundActivityMessage(params: FinalEnvelopeParams.BackgroundActivityParams): SessionMessage {
        return v1Collator.buildFinalBackgroundActivityMessage(params)
    }
}
