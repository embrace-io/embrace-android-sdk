package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.instrumentation.network.DefaultTraceparentGenerator
import io.embrace.android.embracesdk.internal.instrumentation.network.HttpNetworkRequest
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkRequestDataSource
import io.embrace.android.embracesdk.internal.network.http.HttpRequestInfoModifierChain
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpRequestInfoModifier

internal class NetworkRequestApiDelegate(
    private val registry: InstrumentationRegistry,
    private val httpRequestInfoModifierChain: HttpRequestInfoModifierChain,
    private val sessionOrchestrator: SessionOrchestrator,
) : NetworkRequestApi {

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        logNetworkRequest(networkRequest)
    }

    override fun addHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) {
        httpRequestInfoModifierChain.add(modifier)
    }

    override fun removeHttpRequestInfoModifier(modifier: HttpRequestInfoModifier) {
        httpRequestInfoModifierChain.remove(modifier)
    }

    @Deprecated("This is no longer supported")
    override fun generateW3cTraceparent(): String = DefaultTraceparentGenerator.generateW3cTraceparent()

    private fun logNetworkRequest(request: EmbraceNetworkRequest) {
        val req = request.toHttpNetworkRequest()
        val networkRequestDataSource = registry.findByType(NetworkRequestDataSource::class)
        val networkCaptureDataSource = registry.findByType(NetworkCaptureDataSource::class)
        networkRequestDataSource?.recordNetworkRequest(req)
        networkCaptureDataSource?.recordNetworkRequest(req)
        sessionOrchestrator.onSessionDataUpdate()
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
                    dataCaptureErrorMessage = data.dataCaptureErrorMessage,
                )
            },
        )
    }
}
