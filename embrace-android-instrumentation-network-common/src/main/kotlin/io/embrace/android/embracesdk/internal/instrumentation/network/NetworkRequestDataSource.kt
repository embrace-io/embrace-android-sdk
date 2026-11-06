package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource

/**
 * Records network calls made by the application. The Embrace SDK intercepts the calls and reports
 * them to the API.
 */
interface NetworkRequestDataSource : DataSource {

    /**
     * Records a completed network request.
     * This network request is considered unique and finished, meaning that we will not receive additional data for it.
     */
    fun recordNetworkRequest(request: HttpNetworkRequest)

    /**
     * Start a span that will instrument the network request represented by [startData].
     * If the span is successfully started, the W3C traceparent to send with the request when injection applies is returned.
     */
    fun startRequest(startData: RequestStartData): String?

    /**
     * Stop the span that is instrumenting the network request represented by [endData].
     */
    fun endRequest(endData: RequestEndData)

    /**
     * Stop the span of the network request that [startRequest] returned [id] for when its end couldn't be recorded in full, so
     * a request that was made is still reported. Only the end time and [statusCode] are recorded: the request failed if there
     * is no status code, or as [endRequest] judges it from the status code otherwise.
     */
    fun endIncompleteRequest(id: String, sdkClockEndTime: Long, statusCode: Int?)

    /**
     * Stop tracking the network request that [startRequest] returned [id] for, and drop its span without exporting it.
     * Used for a request that never reaches the network, such as one served from a cache.
     */
    fun discardRequest(id: String)
}
