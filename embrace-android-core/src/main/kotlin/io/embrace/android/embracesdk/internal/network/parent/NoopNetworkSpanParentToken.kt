package io.embrace.android.embracesdk.internal.network.parent

import io.embrace.android.embracesdk.network.NetworkSpanParentToken

/**
 * A [NetworkSpanParentToken] for a set that had no effect, so clearing it clears nothing.
 */
object NoopNetworkSpanParentToken : NetworkSpanParentToken
