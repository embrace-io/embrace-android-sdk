package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.SessionZygote
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState

/**
 * Factory that creates session + background activity payloads.
 */
public interface PayloadFactory {

    /**
     * Starts a session in response to a state event.
     */
    public fun startPayloadWithState(state: ProcessState, timestamp: Long, coldStart: Boolean): SessionZygote?

    /**
     * Ends a session in response to a state event.
     */
    public fun endPayloadWithState(
        state: ProcessState,
        timestamp: Long,
        initial: SessionZygote
    ): Envelope<SessionPayload>?

    /**
     * Handles an uncaught exception, ending the session and saving the session to disk.
     */
    public fun endPayloadWithCrash(
        state: ProcessState,
        timestamp: Long,
        initial: SessionZygote,
        crashId: String
    ): Envelope<SessionPayload>?

    /**
     * Provides a snapshot of the active session
     */
    public fun snapshotPayload(state: ProcessState, timestamp: Long, initial: SessionZygote): Envelope<SessionPayload>?

    /**
     * Starts a session manually.
     */
    public fun startSessionWithManual(timestamp: Long): SessionZygote

    /**
     * Ends a session manually.
     */
    public fun endSessionWithManual(timestamp: Long, initial: SessionZygote): Envelope<SessionPayload>
}
