package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.payload.NetworkSessionV2

internal class FakeNetworkLoggingService : NetworkLoggingService {

    var data: NetworkSessionV2 = NetworkSessionV2(emptyList(), emptyMap())

    override fun getNetworkCallsSnapshot(): NetworkSessionV2 =
        data

    override fun logNetworkCall(
        callId: String,
        url: String,
        httpMethod: String,
        statusCode: Int,
        startTime: Long,
        endTime: Long,
        bytesSent: Long,
        bytesReceived: Long,
        traceId: String?,
        w3cTraceparent: String?,
        networkCaptureData: NetworkCaptureData?
    ) {
        TODO("Not yet implemented")
    }

    override fun logNetworkError(
        callId: String,
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        errorType: String?,
        errorMessage: String?,
        traceId: String?,
        w3cTraceparent: String?,
        networkCaptureData: NetworkCaptureData?
    ) {
        TODO("Not yet implemented")
    }

    override fun logNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        TODO("Not yet implemented")
    }

    override fun logURLConnectionNetworkRequest(callId: String, request: EmbraceNetworkRequest) {
        TODO("Not yet implemented")
    }
}
