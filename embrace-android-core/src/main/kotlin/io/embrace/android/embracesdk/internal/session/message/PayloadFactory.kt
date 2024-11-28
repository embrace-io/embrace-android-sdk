package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState

/**
 * Factory that creates payload envelopes.
 */
interface PayloadFactory {

    /**
     * Starts a session in response to a state event.
     */
    fun startPayloadWithState(state: ProcessState, timestamp: Long, coldStart: Boolean): SessionZygote?

    /**
     * Ends a session in response to a state event.
     */
    fun endPayloadWithState(
        state: ProcessState,
        timestamp: Long,
        initial: SessionZygote,
    ): Envelope<SessionPayload>?

    /**
     * Handles an uncaught exception, ending the session and saving the session to disk.
     */
    fun endPayloadWithCrash(
        state: ProcessState,
        timestamp: Long,
        initial: SessionZygote,
        crashId: String,
    ): Envelope<SessionPayload>?

    /**
     * Provides a snapshot of the active session
     */
    fun snapshotPayload(state: ProcessState, timestamp: Long, initial: SessionZygote): Envelope<SessionPayload>?

    /**
     * Starts a session manually.
     */
    fun startSessionWithManual(timestamp: Long): SessionZygote

    /**
     * Ends a session manually.
     */
    fun endSessionWithManual(timestamp: Long, initial: SessionZygote): Envelope<SessionPayload>

    /**
     * Create and return and empty [Envelope] for a [LogPayload] based on the current state of the SDK
     */
    fun createEmptyLogEnvelope(): Envelope<LogPayload>
}
