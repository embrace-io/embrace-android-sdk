package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.message.FinalEnvelopeParams.BackgroundActivityParams
import io.embrace.android.embracesdk.session.message.FinalEnvelopeParams.SessionParams

internal interface PayloadMessageCollator {

    /**
     * Builds a new session object. This should not be sent to the backend but is used
     * to populate essential session information (such as ID), etc
     */
    fun buildInitialSession(params: InitialEnvelopeParams): Session

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
