package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.Initializable
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Abstraction of the current session span
 */
internal interface CurrentSessionSpan : Initializable, SessionSpanWriter {
    /**
     * End the current session span and start a new one if the app is not terminating
     */
    fun endSession(appTerminationCause: AppTerminationCause? = null): List<EmbraceSpanData>

    /**
     * Returns true if a span with the given parameters can be started in the current session
     */
    fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean): Boolean

    /**
     * Returns the current session ID
     */
    fun getSessionId(): String
}
