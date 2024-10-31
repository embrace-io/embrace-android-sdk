package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.Initializable
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Abstraction of the current session span
 */
interface CurrentSessionSpan : Initializable, SessionSpanWriter {
    /**
     * Ensure there exists a session span that is ready to take in data, and create one if it's possible.
     * Returns true if an active session span exists at the time the method returns.
     */
    fun readySession(): Boolean

    /**
     * End the current session span and start a new one if the app is not terminating
     */
    fun endSession(
        startNewSession: Boolean,
        appTerminationCause: AppTerminationCause? = null,
    ): List<EmbraceSpanData>

    /**
     * Returns true if a span with the given parameters can be started in the current session
     */
    fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean): Boolean

    /**
     * Returns the current session ID
     */
    fun getSessionId(): String
}
