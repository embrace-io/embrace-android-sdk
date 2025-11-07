package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource

interface NetworkCaptureDataSource : DataSource {

    /**
     * Records the network request body if the request matches the rule criteria.
     */
    fun recordNetworkRequest(request: HttpNetworkRequest)

    /**
     * Whether network request body capture is enabled for the given URL and method.
     *
     * This method is discouraged and will be removed soon.
     */
    fun shouldCaptureNetworkBody(url: String, method: String): Boolean
}
