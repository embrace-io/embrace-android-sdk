package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

/**
 * The public API that is used for capturing network requests manually
 */
@InternalApi
public interface NetworkRequestApi {

    /**
     * Logs the fact that a network call occurred. These are recorded and sent to Embrace as part
     * of a particular session.
     *
     * You can create an instance of [EmbraceNetworkRequest] using the factory functions.
     */
    public fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest)

    public val traceIdHeader: String

    public fun generateW3cTraceparent(): String
}
