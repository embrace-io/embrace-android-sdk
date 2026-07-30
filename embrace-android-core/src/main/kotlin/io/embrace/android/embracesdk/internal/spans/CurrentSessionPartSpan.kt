package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.Initializable
import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Abstraction of the current session part span
 */
interface CurrentSessionPartSpan : Initializable {
    /**
     * Ensure there exists a session part span that is ready to take in data, and create one if it's possible.
     * Returns true if an active session part span exists at the time the method returns.
     */
    fun readySession(): Boolean

    /**
     * End the current session part span and start a new one if the app is not terminating
     */
    fun endSession(
        startNewSession: Boolean,
        appTerminationCause: AppTerminationCause? = null,
    ): List<Span>

    /**
     * Returns true if a span with the given parameters can be started in the current session
     */
    fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean): Boolean

    /**
     * Returns the current session part ID
     */
    fun getId(): String

    /**
     * Callback to be invoked when a span is just about to be stopped
     */
    fun spanStopCallback(spanId: String)

    /**
     * Returns the current session part span, if any.
     */
    fun current(): EmbraceSdkSpan?
}
