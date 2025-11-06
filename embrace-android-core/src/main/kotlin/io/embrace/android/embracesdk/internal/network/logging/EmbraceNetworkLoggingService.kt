package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.instrumentation.network.HttpNetworkRequest
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getDomain
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getUrlPath
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getValidTraceId
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.stripUrl
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.semconv.ErrorAttributes
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.HttpAttributes
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi

/**
 * Logs network calls according to defined limits per domain.
 *
 *
 * Limits can be defined either in server-side configuration or within the embrace configuration file.
 * A limit of 0 disables logging for the domain. All network calls are captured up to the limit,
 * and the number of calls is also captured if the limit is exceeded.
 */
internal class EmbraceNetworkLoggingService(
    private val domainCountLimiter: DomainCountLimiter,
    private val spanService: SpanService,
) : NetworkLoggingService {

    override fun logNetworkRequest(request: HttpNetworkRequest) {
        // Get the domain, if it can be successfully parsed. If not, don't log this call.
        val domain = getDomain(
            stripUrl(request.url)
        ) ?: return

        if (domainCountLimiter.canLogNetworkRequest(domain)) {
            val strippedUrl = stripUrl(request.url)

            val networkRequestSchemaType = SchemaType.NetworkRequest(generateSchemaAttributes(request))
            val statusCode = request.statusCode
            val errorCode = if (statusCode == null || statusCode <= 0 || statusCode >= 400) {
                ErrorCode.FAILURE
            } else {
                null
            }
            spanService.recordCompletedSpan(
                name = "${request.httpMethod} ${getUrlPath(strippedUrl)}",
                startTimeMs = request.startTime,
                endTimeMs = request.endTime,
                type = EmbType.Performance.Network,
                attributes = networkRequestSchemaType.attributes(),
                errorCode = errorCode,
            )
        }
    }

    @OptIn(IncubatingApi::class)
    private fun generateSchemaAttributes(request: HttpNetworkRequest): Map<String, String> = mapOf(
        "url.full" to stripUrl(request.url),
        HttpAttributes.HTTP_REQUEST_METHOD to request.httpMethod,
        HttpAttributes.HTTP_RESPONSE_STATUS_CODE to request.statusCode,
        HttpAttributes.HTTP_REQUEST_BODY_SIZE to request.bytesSent,
        HttpAttributes.HTTP_RESPONSE_BODY_SIZE to request.bytesReceived,
        ErrorAttributes.ERROR_TYPE to request.errorType,
        ExceptionAttributes.EXCEPTION_MESSAGE to request.errorMessage,
        "emb.w3c_traceparent" to request.w3cTraceparent,
        "emb.trace_id" to getValidTraceId(request.traceId),
    ).toNonNullMap().mapValues { it.value.toString() }
}
