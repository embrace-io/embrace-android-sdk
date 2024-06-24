package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.payload.SessionZygote

internal interface PayloadMessageCollator {

    /**
     * Builds a new precursor session object. This is not sent to the backend but is used
     * to hold essential session information (such as ID), etc
     */
    fun buildInitialSession(params: InitialEnvelopeParams): SessionZygote

    /**
     * Builds a fully populated payload.
     */
    fun buildFinalEnvelope(params: FinalEnvelopeParams): Envelope<SessionPayload>
}
