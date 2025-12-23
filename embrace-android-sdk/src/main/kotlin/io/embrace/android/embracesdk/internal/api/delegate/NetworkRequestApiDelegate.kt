package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.instrumentation.network.DefaultTraceparentGenerator
import io.embrace.android.embracesdk.internal.instrumentation.network.HttpNetworkRequest
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkRequestDataSource
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class NetworkRequestApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : NetworkRequestApi {

    private val configService by embraceImplInject(sdkCallChecker) { bootstrapper.configService }
    private val registry by embraceImplInject(sdkCallChecker) {
        bootstrapper.instrumentationModule.instrumentationRegistry
    }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) {
        bootstrapper.sessionOrchestrator
    }

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        if (sdkCallChecker.check("record_network_request")) {
            logNetworkRequest(networkRequest)
        }
    }

    override fun generateW3cTraceparent(): String? =
        if (configService?.networkSpanForwardingBehavior?.isNetworkSpanForwardingEnabled() == true) {
            DefaultTraceparentGenerator.generateW3cTraceparent()
        } else {
            null
        }

    private fun logNetworkRequest(request: EmbraceNetworkRequest) {
        val req = request.toHttpNetworkRequest()
        val networkRequestDataSource = registry?.findByType(NetworkRequestDataSource::class)
        val networkCaptureDataSource = registry?.findByType(NetworkCaptureDataSource::class)
        networkRequestDataSource?.recordNetworkRequest(req)
        networkCaptureDataSource?.recordNetworkRequest(req)
        sessionOrchestrator?.onSessionDataUpdate()
    }

    private fun EmbraceNetworkRequest.toHttpNetworkRequest(): HttpNetworkRequest {
        return HttpNetworkRequest(
            url = url,
            httpMethod = httpMethod,
            startTime = startTime,
            endTime = endTime,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
            statusCode = responseCode,
            errorType = errorType,
            errorMessage = errorMessage,
            traceId = traceId,
            w3cTraceparent = w3cTraceparent,
            body = networkCaptureData?.let { data ->
                HttpNetworkRequest.HttpRequestBody(
                    requestHeaders = data.requestHeaders,
                    requestQueryParams = data.requestQueryParams,
                    capturedRequestBody = data.capturedRequestBody,
                    responseHeaders = data.responseHeaders,
                    capturedResponseBody = data.capturedResponseBody,
                    dataCaptureErrorMessage = data.dataCaptureErrorMessage
                )
            }
        )
    }
}
