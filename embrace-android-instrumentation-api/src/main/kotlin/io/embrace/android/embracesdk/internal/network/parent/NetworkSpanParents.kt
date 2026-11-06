package io.embrace.android.embracesdk.internal.network.parent

import io.embrace.android.embracesdk.network.NetworkSpanParent
import io.embrace.android.embracesdk.network.http.HttpRequestInfo

/**
 * Supplies the network span parents the app has set, for network instrumentation to apply.
 */
interface NetworkSpanParents {

    /**
     * Returns the network span parent currently set, or null if none is.
     */
    fun currentParent(): NetworkSpanParent?

    /**
     * Returns the decision of the app's network span parent provider for [request], or [NetworkSpanParent.USE_DEFAULT] if it has none.
     */
    fun appDecision(request: HttpRequestInfo): NetworkSpanParent
}
