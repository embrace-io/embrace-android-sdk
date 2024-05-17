package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

/**
 * The public API that is used for capturing network requests manually
 */
internal interface NetworkRequestApi {

    /**
     * Logs the fact that a network call occurred. These are recorded and sent to Embrace as part
     * of a particular session.
     *
     * You can create an instance of [EmbraceNetworkRequest] using the factory functions.
     */
    fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest)

    fun getTraceIdHeader(): String

    fun generateW3cTraceparent(): String
}
