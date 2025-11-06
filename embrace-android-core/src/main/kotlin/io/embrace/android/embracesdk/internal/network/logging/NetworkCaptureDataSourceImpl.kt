package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall

internal class NetworkCaptureDataSourceImpl(
    args: InstrumentationArgs,
) : NetworkCaptureDataSource, DataSourceImpl(
    args = args,
    limitStrategy = NoopLimitStrategy,
) {

    /**
     * Creates a log with data from a captured network request.
     */
    override fun logNetworkCapturedCall(call: NetworkCapturedCall) {
        captureTelemetry {
            addLog(
                SchemaType.NetworkCapturedRequest(
                    duration = call.duration,
                    endTime = call.endTime,
                    httpMethod = call.httpMethod,
                    matchedUrl = call.matchedUrl,
                    networkId = call.networkId,
                    requestBody = call.requestBody,
                    requestBodySize = call.requestBodySize,
                    requestQuery = call.requestQuery,
                    requestQueryHeaders = call.requestQueryHeaders,
                    requestSize = call.requestSize,
                    responseBody = call.responseBody,
                    responseBodySize = call.responseBodySize,
                    responseHeaders = call.responseHeaders,
                    responseSize = call.responseSize,
                    responseStatus = call.responseStatus,
                    sessionId = call.sessionId,
                    startTime = call.startTime,
                    url = call.url,
                    errorMessage = call.errorMessage,
                    encryptedPayload = call.encryptedPayload
                ),
                LogSeverity.INFO,
                call.networkId
            )
        }
    }
}
