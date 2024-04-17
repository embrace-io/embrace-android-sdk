package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.payload.NetworkSessionV2

/**
 * Logs network calls made by the application. The Embrace SDK intercepts the calls and reports
 * them to the API.
 */
internal interface NetworkLoggingService {

    /**
     * Logs a network request.
     * This network request is considered unique and finished, meaning that we will not receive additional data for it.
     *
     * @param networkRequest the network request to log
     */
    fun logNetworkRequest(networkRequest: EmbraceNetworkRequest)

    /**
     * Logs a network request.
     *
     * We might get multiple calls to this method for the same callId, in which case we will only keep the last one.
     * This is only used by the EmbraceUrlConnectionDelegate, where we don't know the point at which the request is finished, so we call
     * this method multiple times with whatever we have at the time.
     *
     * //TODO: Multiple calls might bring different info? Or is it always the same request with more data?
     *
     * @param callId                the ID with which the request will be identified internally. The session will only contain one recorded
     *                              request with a given ID - last writer wins.
     * @param request the request to be recorded
     */
    fun logURLConnectionNetworkRequest(callId: String, request: EmbraceNetworkRequest)
}
