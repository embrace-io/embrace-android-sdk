package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.Initializable
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Abstraction of the current session span
 */
public interface CurrentSessionSpan : Initializable, SessionSpanWriter {
    /**
     * End the current session span and start a new one if the app is not terminating
     */
    public fun endSession(
        startNewSession: Boolean,
        appTerminationCause: AppTerminationCause? = null
    ): List<EmbraceSpanData>

    /**
     * Returns true if a span with the given parameters can be started in the current session
     */
    public fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean): Boolean

    /**
     * Returns the current session ID
     */
    public fun getSessionId(): String
}