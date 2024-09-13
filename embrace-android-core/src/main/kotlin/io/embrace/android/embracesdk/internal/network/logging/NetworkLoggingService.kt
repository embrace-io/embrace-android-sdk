package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

/**
 * Logs network calls made by the application. The Embrace SDK intercepts the calls and reports
 * them to the API.
 */
interface NetworkLoggingService {

    /**
     * Logs a network request.
     * This network request is considered unique and finished, meaning that we will not receive additional data for it.
     *
     * @param networkRequest the network request to log
     */
    fun logNetworkRequest(networkRequest: EmbraceNetworkRequest)
}
