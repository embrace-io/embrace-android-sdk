package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.lifecycle.ProcessState

/**
 * Factory that creates session + background activity payloads.
 */
internal interface PayloadFactory {

    /**
     * Starts a session in response to a state event.
     */
    fun startPayloadWithState(state: ProcessState, timestamp: Long, coldStart: Boolean): Session?

    /**
     * Ends a session in response to a state event.
     */
    fun endPayloadWithState(state: ProcessState, timestamp: Long, initial: Session): SessionMessage?

    /**
     * Handles an uncaught exception, ending the session and saving the session to disk.
     */
    fun endPayloadWithCrash(
        state: ProcessState,
        timestamp: Long,
        initial: Session,
        crashId: String
    ): SessionMessage?

    /**
     * Provides a snapshot of the active session
     */
    fun snapshotPayload(state: ProcessState, timestamp: Long, initial: Session): SessionMessage?

    /**
     * Starts a session manually.
     */
    fun startSessionWithManual(timestamp: Long): Session

    /**
     * Ends a session manually.
     */
    fun endSessionWithManual(timestamp: Long, initial: Session): SessionMessage
}
