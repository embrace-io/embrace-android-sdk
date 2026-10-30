package io.embrace.android.embracesdk.internal.network.parent

import io.embrace.android.embracesdk.network.NetworkSpanParentProvider
import io.embrace.android.embracesdk.network.NetworkSpanParentToken
import io.opentelemetry.kotlin.tracing.SpanContext

/**
 * Holds the network span parent that network spans started from now on are parented to.
 */
interface NetworkSpanParentService {

    /**
     * Decides the parent of each network span from the network span parent currently set.
     */
    val provider: NetworkSpanParentProvider

    /**
     * Sets [spanContext] as the network span parent, returning a token that clears only this set, or one that clears nothing if rejected.
     */
    fun set(spanContext: SpanContext?): NetworkSpanParentToken

    /**
     * Clears the network span parent if [token] is from the most recent set.
     */
    fun clear(token: NetworkSpanParentToken)

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
