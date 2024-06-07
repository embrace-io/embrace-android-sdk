package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.SessionZygote
import io.embrace.android.embracesdk.session.lifecycle.ProcessState

/**
 * Factory that creates session + background activity payloads.
 */
internal interface PayloadFactory {

    /**
     * Starts a session in response to a state event.
     */
    fun startPayloadWithState(state: ProcessState, timestamp: Long, coldStart: Boolean): SessionZygote?

    /**
     * Ends a session in response to a state event.
     */
    fun endPayloadWithState(state: ProcessState, timestamp: Long, initial: SessionZygote): SessionMessage?

    /**
     * Handles an uncaught exception, ending the session and saving the session to disk.
     */
    fun endPayloadWithCrash(
        state: ProcessState,
        timestamp: Long,
        initial: SessionZygote,
        crashId: String
    ): SessionMessage?

    /**
     * Provides a snapshot of the active session
     */
    fun snapshotPayload(state: ProcessState, timestamp: Long, initial: SessionZygote): SessionMessage?

    /**
     * Starts a session manually.
     */
    fun startSessionWithManual(timestamp: Long): SessionZygote

    /**
     * Ends a session manually.
     */
    fun endSessionWithManual(timestamp: Long, initial: SessionZygote): SessionMessage
}
