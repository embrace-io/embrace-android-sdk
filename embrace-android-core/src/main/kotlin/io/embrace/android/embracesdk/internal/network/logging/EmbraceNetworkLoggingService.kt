package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.network.logging.EmbraceNetworkCaptureService.Companion.NETWORK_ERROR_CODE
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getDomain
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getUrlPath
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getValidTraceId
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.stripUrl
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.semconv.ErrorAttributes
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes

/**
 * Logs network calls according to defined limits per domain.
 *
 *
 * Limits can be defined either in server-side configuration or within the embrace configuration file.
 * A limit of 0 disables logging for the domain. All network calls are captured up to the limit,
 * and the number of calls is also captured if the limit is exceeded.
 */
internal class EmbraceNetworkLoggingService(
    private val embraceDomainCountLimiter: DomainCountLimiter,
    private val networkCaptureService: NetworkCaptureService,
    private val spanService: SpanService
) : NetworkLoggingService {

    override fun logNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        logNetworkCaptureData(networkRequest)
        recordNetworkRequest(networkRequest)
    }

    private fun logNetworkCaptureData(networkRequest: EmbraceNetworkRequest) {
        if (networkRequest.networkCaptureData != null) {
            networkCaptureService.logNetworkCapturedData(
                networkRequest.url, // TODO: This used the non-stripped URL, is that correct?
                networkRequest.httpMethod,
                networkRequest.responseCode ?: NETWORK_ERROR_CODE,
                networkRequest.startTime,
                networkRequest.endTime,
                networkRequest.networkCaptureData,
                networkRequest.errorMessage
            )
        }
    }

    /**
     * Records network calls as spans if their domain can be parsed and is within the limits.
     *
     * @param networkRequest the network request to record
     */
    private fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        // TODO: Shouldn't we ignore domains that can't be parsed for networkCapturedData too?
        // Shouldn't we also track limits for networkCapturedData?

        // Get the domain, if it can be successfully parsed. If not, don't log this call.
        val domain = getDomain(
            stripUrl(networkRequest.url)
        ) ?: return

        if (embraceDomainCountLimiter.canLogNetworkRequest(domain)) {
            val strippedUrl = stripUrl(networkRequest.url)

            val networkRequestSchemaType = SchemaType.NetworkRequest(generateSchemaAttributes(networkRequest))
            val statusCode = networkRequest.responseCode
            val errorCode = if (statusCode == null || statusCode <= 0 || statusCode >= 400) {
                ErrorCode.FAILURE
            } else {
                null
            }
            spanService.recordCompletedSpan(
                name = "${networkRequest.httpMethod} ${getUrlPath(strippedUrl)}",
                startTimeMs = networkRequest.startTime,
                endTimeMs = networkRequest.endTime,
                errorCode = errorCode,
                attributes = networkRequestSchemaType.attributes(),
                type = EmbType.Performance.Network,
            )
        }
    }

    private fun generateSchemaAttributes(networkRequest: EmbraceNetworkRequest): Map<String, String> = mapOf(
        "url.full" to stripUrl(networkRequest.url),
        HttpAttributes.HTTP_REQUEST_METHOD.key to networkRequest.httpMethod,
        HttpAttributes.HTTP_RESPONSE_STATUS_CODE.key to networkRequest.responseCode,
        HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE.key to networkRequest.bytesSent,
        HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE.key to networkRequest.bytesReceived,
        ErrorAttributes.ERROR_TYPE.key to networkRequest.errorType,
        ExceptionAttributes.EXCEPTION_MESSAGE.key to networkRequest.errorMessage,
        "emb.w3c_traceparent" to networkRequest.w3cTraceparent,
        "emb.trace_id" to getValidTraceId(networkRequest.traceId),
    ).toNonNullMap().mapValues { it.value.toString() }
}
