package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.SessionPartToken

interface PayloadMessageCollator {

    /**
     * Builds a new precursor part. This is not sent to the backend but is used
     * to hold essential session information (such as ID), etc
     */
    fun buildInitialPart(params: InitialEnvelopeParams): SessionPartToken

    /**
     * Builds a fully populated payload.
     */
    fun buildFinalEnvelope(params: FinalEnvelopeParams): Envelope<SessionPartPayload>
}
