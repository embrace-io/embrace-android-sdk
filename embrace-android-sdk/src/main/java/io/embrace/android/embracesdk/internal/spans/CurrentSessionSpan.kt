package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.Initializable
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.api.trace.Span

/**
 * Abstraction of the current session span
 */
internal interface CurrentSessionSpan : Initializable {
    /**
     * End the current session span and start a new one if the app is not terminating
     */
    fun endSession(appTerminationCause: EmbraceAttributes.AppTerminationCause? = null): List<EmbraceSpanData>

    /**
     * Returns true if a span with the given parameters can be started in the current session
     */
    fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean): Boolean

    /**
     * Retrieves the current session span If a session span does not exist this will return
     * null.
     */
    fun retrieveSessionSpan(): Span?
}
