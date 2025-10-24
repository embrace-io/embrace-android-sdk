package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSourceImpl
import io.embrace.android.embracesdk.internal.arch.destination.LogSeverity
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall

internal class NetworkCaptureDataSourceImpl(
    private val logWriter: LogWriter,
    logger: EmbLogger,
) : NetworkCaptureDataSource,
    LogDataSourceImpl(
        destination = logWriter,
        logger = logger,
        limitStrategy = NoopLimitStrategy,
    ) {

    /**
     * Creates a log with data from a captured network request.
     *
     * @param networkCapturedCall the captured network information
     */
    override fun logNetworkCapturedCall(networkCapturedCall: NetworkCapturedCall) {
        return logWriter.addLog(
            SchemaType.NetworkCapturedRequest(
                duration = networkCapturedCall.duration,
                endTime = networkCapturedCall.endTime,
                httpMethod = networkCapturedCall.httpMethod,
                matchedUrl = networkCapturedCall.matchedUrl,
                networkId = networkCapturedCall.networkId,
                requestBody = networkCapturedCall.requestBody,
                requestBodySize = networkCapturedCall.requestBodySize,
                requestQuery = networkCapturedCall.requestQuery,
                requestQueryHeaders = networkCapturedCall.requestQueryHeaders,
                requestSize = networkCapturedCall.requestSize,
                responseBody = networkCapturedCall.responseBody,
                responseBodySize = networkCapturedCall.responseBodySize,
                responseHeaders = networkCapturedCall.responseHeaders,
                responseSize = networkCapturedCall.responseSize,
                responseStatus = networkCapturedCall.responseStatus,
                sessionId = networkCapturedCall.sessionId,
                startTime = networkCapturedCall.startTime,
                url = networkCapturedCall.url,
                errorMessage = networkCapturedCall.errorMessage,
                encryptedPayload = networkCapturedCall.encryptedPayload
            ),
            LogSeverity.INFO,
            networkCapturedCall.networkId
        )
    }
}
