package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.SessionZygote
import io.embrace.android.embracesdk.session.message.FinalEnvelopeParams.BackgroundActivityParams
import io.embrace.android.embracesdk.session.message.FinalEnvelopeParams.SessionParams

internal interface PayloadMessageCollator {

    /**
     * Builds a new precursor session object. This is not sent to the backend but is used
     * to hold essential session information (such as ID), etc
     */
    fun buildInitialSession(params: InitialEnvelopeParams): SessionZygote

    /**
     * Builds a fully populated session message. This can be sent to the backend (or stored
     * on disk).
     */
    fun buildFinalSessionMessage(params: SessionParams): SessionMessage

    /**
     * Create the background session message with the current state of the background activity.
     */
    fun buildFinalBackgroundActivityMessage(params: BackgroundActivityParams): SessionMessage
}
