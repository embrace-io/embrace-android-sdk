package io.embrace.android.embracesdk.internal.network.parent

import io.embrace.android.embracesdk.network.NetworkSpanParentToken
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.kotlin.tracing.SpanContext

/**
 * Holds the network span parent that network spans started from now on are parented to.
 */
interface NetworkSpanParentService {

    /**
     * Sets [span] as the network span parent as for its span context, rejecting it if it isn't recording.
     */
    fun set(span: EmbraceSpan): NetworkSpanParentToken

    /**
     * Sets [spanContext] as the network span parent, returning a token that clears only this set, or one that clears nothing if rejected.
     */
    fun set(spanContext: SpanContext?): NetworkSpanParentToken

    /**
     * Clears the network span parent and returns true if [token] is from the most recent set.
     */
    fun clear(token: NetworkSpanParentToken): Boolean

    /**
     * Clears the network span parent unconditionally.
     */
    fun clear()

    /**
     * Returns the network span parent currently set, or null if none is.
     */
    fun currentParent(): SpanContext?

    /**
     * Clears the network span parent if it is the span with [spanId], which has just stopped.
     */
    fun onSpanStopped(spanId: String)

    /**
     * Clears the network span parent if it was set during the user session with [userSessionId], which has just ended.
     */
    fun onUserSessionEnded(userSessionId: String)
}
