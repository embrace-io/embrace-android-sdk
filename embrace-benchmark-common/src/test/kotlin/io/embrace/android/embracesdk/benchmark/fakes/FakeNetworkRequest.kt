package io.embrace.android.embracesdk.benchmark.fakes

import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.CompletedNetworkRequestDsl
import io.embrace.android.embracesdk.network.IncompleteNetworkRequestDsl
import io.embrace.android.embracesdk.network.NetworkRequestDsl
import io.embrace.android.embracesdk.network.http.HttpMethod

internal class FakeNetworkRequest(
    val url: String,
    val httpMethod: HttpMethod,
    val startTime: Long,
    val endTime: Long,
) : NetworkRequestDsl, CompletedNetworkRequestDsl, IncompleteNetworkRequestDsl {
    override var traceId: String? = null
    override var w3cTraceparent: String? = null
    override var networkCaptureData: NetworkCaptureData? = null
    override var statusCode: Int? = null
    override var bytesSent: Long? = null
    override var bytesReceived: Long? = null
    override var errorType: String? = null
    override var errorMessage: String? = null

    override fun completed(action: CompletedNetworkRequestDsl.() -> Unit) = action()

    override fun incomplete(action: IncompleteNetworkRequestDsl.() -> Unit) = action()
}
