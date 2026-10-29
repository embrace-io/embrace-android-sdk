package io.embrace.android.embracesdk.internal.network.parent

import io.embrace.android.embracesdk.network.NetworkSpanParent

/**
 * Supplies the network span parents the app has set, for network instrumentation to apply.
 */
fun interface NetworkSpanParents {

    /**
     * Returns the network span parent currently set, or null if none is.
     */
    fun currentParent(): NetworkSpanParent?
}
