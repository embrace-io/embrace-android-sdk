package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource

/**
 * Records network calls made by the application. The Embrace SDK intercepts the calls and reports
 * them to the API.
 */
interface NetworkRequestDataSource : DataSource {

    /**
     * Records a network request.
     * This network request is considered unique and finished, meaning that we will not receive additional data for it.
     */
    fun recordNetworkRequest(request: HttpNetworkRequest)
}
