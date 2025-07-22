package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Logs network calls made by the application. The Embrace SDK intercepts the calls and reports
 * them to the API.
 */
interface NetworkLoggingService {

    /**
     * Logs a completed network request. This is equivalent to calling [startNetworkRequestSpan] and [endNetworkRequestSpan].
     */
    fun logNetworkRequest(networkRequest: EmbraceNetworkRequest)

    /**
     * Attempts to start a span that models a network request, returning a span object if this is allowed.
     */
    fun startNetworkRequestSpan(
        httpMethod: HttpMethod,
        url: String,
        startTimeMs: Long,
    ): EmbraceSpan?

    /**
     * Ends a span that is modelling a network request.
     */
    fun endNetworkRequestSpan(
        networkRequest: EmbraceNetworkRequest,
        span: EmbraceSpan,
    )
}
