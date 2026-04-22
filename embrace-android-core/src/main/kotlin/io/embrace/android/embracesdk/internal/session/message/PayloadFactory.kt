package io.embrace.android.embracesdk.internal.session.message

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.SessionPartToken

/**
 * Factory that creates payload envelopes.
 */
interface PayloadFactory {

    /**
     * Starts a session in response to a state event.
     */
    fun startPayloadWithState(
        state: AppState,
        timestamp: Long,
        coldStart: Boolean,
        partNumber: Int
    ): SessionPartToken?

    /**
     * Ends a session in response to a state event.
     */
    fun endPayloadWithState(
        state: AppState,
        timestamp: Long,
        initial: SessionPartToken,
    ): Envelope<SessionPartPayload>?

    /**
     * Handles an uncaught exception, ending the session and saving the session to disk.
     */
    fun endPayloadWithCrash(
        state: AppState,
        timestamp: Long,
        initial: SessionPartToken,
        crashId: String,
    ): Envelope<SessionPartPayload>?

    /**
     * Provides a snapshot of the active session
     */
    fun snapshotPayload(state: AppState, timestamp: Long, initial: SessionPartToken): Envelope<SessionPartPayload>?

    /**
     * Starts a session manually.
     */
    fun startSessionWithManual(timestamp: Long, partNumber: Int): SessionPartToken

    /**
     * Ends a session manually.
     */
    fun endSessionWithManual(timestamp: Long, initial: SessionPartToken): Envelope<SessionPartPayload>

    /**
     * Create and return and empty [Envelope] for a [LogPayload] based on the current state of the SDK
     */
    fun createEmptyLogEnvelope(): Envelope<LogPayload>
}
