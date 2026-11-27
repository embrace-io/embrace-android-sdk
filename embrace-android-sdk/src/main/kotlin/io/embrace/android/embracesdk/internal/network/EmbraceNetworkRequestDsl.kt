package io.embrace.android.embracesdk.internal.network

import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.CompletedNetworkRequestDsl
import io.embrace.android.embracesdk.network.IncompleteNetworkRequestDsl
import io.embrace.android.embracesdk.network.NetworkRequestDsl
import io.embrace.android.embracesdk.network.http.HttpMethod

/**
 * Implements every network request scope so recording a request allocates one object. Entering a completed or
 * incomplete scope clears the other's properties, so only the last one called is kept.
 */
internal class EmbraceNetworkRequestDsl(
    private val url: String,
    private val httpMethod: HttpMethod,
    private val startTime: Long,
    private val endTime: Long,
) : NetworkRequestDsl, CompletedNetworkRequestDsl, IncompleteNetworkRequestDsl {

    override var traceId: String? = null
    override var w3cTraceparent: String? = null
    override var networkCaptureData: NetworkCaptureData? = null
    override var statusCode: Int? = null
    override var bytesSent: Long? = null
    override var bytesReceived: Long? = null
    override var errorType: String? = null
    override var errorMessage: String? = null

    override fun completed(action: CompletedNetworkRequestDsl.() -> Unit) {
        errorType = null
        errorMessage = null
        action()
    }

    override fun incomplete(action: IncompleteNetworkRequestDsl.() -> Unit) {
        statusCode = null
        bytesSent = null
        bytesReceived = null
        action()
    }

    fun build(): EmbraceNetworkRequest = EmbraceNetworkRequest(
        url = url,
        httpMethod = httpMethod.name,
        startTime = startTime,
        endTime = endTime,
        bytesSent = bytesSent,
        bytesReceived = bytesReceived,
        responseCode = statusCode,
        errorType = errorType,
        errorMessage = errorMessage,
        traceId = traceId,
        w3cTraceparent = w3cTraceparent,
        networkCaptureData = networkCaptureData,
    )
}
