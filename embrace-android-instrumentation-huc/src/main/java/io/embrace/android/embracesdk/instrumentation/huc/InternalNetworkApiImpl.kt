package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.instrumentation.network.HttpNetworkRequest
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkRequestDataSource
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class InternalNetworkApiImpl(
    private val args: InstrumentationArgs,
    private val networkRequestDataSource: NetworkRequestDataSource?,
    private val networkCaptureDataSource: NetworkCaptureDataSource?,
) : InternalNetworkApi {

    override fun getSdkCurrentTimeMs(): Long = args.clock.now()

    override fun isNetworkSpanForwardingEnabled(): Boolean =
        args.configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled()

    override fun recordNetworkRequest(embraceNetworkRequest: EmbraceNetworkRequest) {
        val body = embraceNetworkRequest.networkCaptureData
        networkRequestDataSource?.recordNetworkRequest(
            HttpNetworkRequest(
                url = embraceNetworkRequest.url,
                httpMethod = embraceNetworkRequest.httpMethod,
                startTime = embraceNetworkRequest.startTime,
                endTime = embraceNetworkRequest.endTime,
                bytesSent = embraceNetworkRequest.bytesSent,
                bytesReceived = embraceNetworkRequest.bytesReceived,
                statusCode = embraceNetworkRequest.responseCode,
                errorType = embraceNetworkRequest.errorType,
                errorMessage = embraceNetworkRequest.errorMessage,
                traceId = embraceNetworkRequest.traceId,
                w3cTraceparent = embraceNetworkRequest.w3cTraceparent,
                body = HttpNetworkRequest.HttpRequestBody(
                    requestHeaders = body?.requestHeaders,
                    requestQueryParams = body?.requestQueryParams,
                    capturedRequestBody = body?.capturedRequestBody,
                    responseHeaders = body?.responseHeaders,
                    capturedResponseBody = body?.capturedResponseBody,
                    dataCaptureErrorMessage = body?.dataCaptureErrorMessage,
                )
            )
        )
    }

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean {
        return networkCaptureDataSource?.shouldCaptureNetworkBody(
            url = url,
            method = method
        ) ?: false
    }

    override fun logInternalError(error: Throwable) = args.logger.trackInternalError(InternalErrorType.INTERNAL_INTERFACE_FAIL, error)
}
