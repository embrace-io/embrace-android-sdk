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
     * If the span is successfully started, its W3C traceparent representation will be returned.
     */
    fun startRequest(startData: RequestStartData): String?

    /**
     * Stop the span that is instrumenting the network request represented by [endData].
     */
    fun endRequest(endData: RequestEndData)
}
