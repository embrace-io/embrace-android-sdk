package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.payload.NetworkSessionV2

internal class FakeNetworkLoggingService : NetworkLoggingService {

    var data: NetworkSessionV2 = NetworkSessionV2(emptyList(), emptyMap())

    override fun getNetworkCallsSnapshot(): NetworkSessionV2 =
        data

    override fun endNetworkRequest(
        callId: String,
        statusCode: Int,
        endTime: Long,
        bytesSent: Long,
        bytesReceived: Long,
        networkCaptureData: NetworkCaptureData?
    ) {
        TODO("Not yet implemented")
    }

    override fun endNetworkRequestWithError(
        callId: String,
        endTime: Long,
        errorType: String?,
        errorMessage: String?,
        networkCaptureData: NetworkCaptureData?
    ) {
        TODO("Not yet implemented")
    }

    override fun startNetworkCall(
        callId: String,
        url: String,
        httpMethod: String,
        statusCode: Int,
        startTime: Long,
        traceId: String?,
        w3cTraceparent: String?
    ) {
        TODO("Not yet implemented")
    }
}
