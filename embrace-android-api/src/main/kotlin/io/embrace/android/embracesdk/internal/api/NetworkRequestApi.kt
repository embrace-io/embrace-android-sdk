package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

/**
 * The public API that is used for capturing network requests manually
 */
public interface NetworkRequestApi {

    /**
     * Logs the fact that a network call occurred. These are recorded and sent to Embrace as part
     * of a particular session.
     *
     * You can create an instance of [EmbraceNetworkRequest] using the factory functions.
     */
    public fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest)

    public fun getTraceIdHeader(): String

    public fun generateW3cTraceparent(): String
}
