package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.network.http.MutableHttpRequestInfoImpl
import io.embrace.android.embracesdk.internal.network.logging.DomainCountLimiter
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getDomain
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getUrlPath
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getValidTraceId
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.stripUrl
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import io.embrace.android.embracesdk.semconv.EmbNetworkRequestAttributes
import io.opentelemetry.kotlin.semconv.ErrorAttributes
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.opentelemetry.kotlin.semconv.HttpAttributes
import io.opentelemetry.kotlin.semconv.UrlAttributes
import io.opentelemetry.kotlin.semconv.UserAgentAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * Logs network calls according to defined limits per domain.
 *
 * Limits can be defined either in server-side configuration or within the embrace configuration file.
 * A limit of 0 disables logging for the domain. All network calls are captured up to the limit,
 * and the number of calls is also captured if the limit is exceeded.
 */
class NetworkRequestDataSourceImpl(
    args: InstrumentationArgs,
) : NetworkRequestDataSource, DataSourceImpl(
    args,
    NoopLimitStrategy,
    "network_request_data_source",
) {
    private val activeRequests: MutableMap<String, ActiveRequest> = ConcurrentHashMap()
    private val domainCountLimiter: DomainCountLimiter = args.configService.networkBehavior.domainCountLimiter
    private val httpRequestInfoModifierChain = args.httpRequestInfoModifierChain

    override fun recordNetworkRequest(request: HttpNetworkRequest) {
        // Apply any registered modifiers so the reported url/method reflect the consumer's changes.
        // The underlying HTTP request is not affected.
        val info = httpRequestInfoModifierChain.apply(MutableHttpRequestInfoImpl(request.httpMethod, request.url))
        val url = info.url
        val httpMethod = info.httpMethod

        if (!configService.networkBehavior.isUrlEnabled(url)) {
            return
        }

        // Get the domain, if it can be successfully parsed. If not, don't log this call.
        val domain = getDomain(
            stripUrl(url),
        ) ?: return

        captureTelemetry(
            inputValidation = {
                domainCountLimiter.canLogNetworkRequest(domain)
            },
            invalidInputCallback = {
                telemetryService.trackAppliedLimit("network_request", AppliedLimitType.DROP)
            },
        ) {
            val networkRequestSchemaType = SchemaType.NetworkRequest(generateSchemaAttributes(request, url, httpMethod))
            val statusCode = request.statusCode
            val errorCode = if (statusCode == null || statusCode <= 0 || statusCode >= 400) {
                ErrorCodeAttribute.Failure
            } else {
                null
            }
            recordCompletedSpan(
                name = getNetworkSpanName(httpMethod, url),
                startTimeMs = request.startTime,
                endTimeMs = request.endTime,
                type = EmbType.Performance.Network,
                attributes = networkRequestSchemaType.attributes(),
                errorCode = errorCode,
            )
        }
    }

    override fun startRequest(startData: RequestStartData): String? {
        // Apply any registered modifiers so the reported url/method reflect the consumer's changes.
        // The underlying HTTP request is not affected.
        val info = httpRequestInfoModifierChain.apply(MutableHttpRequestInfoImpl(startData.httpMethod, startData.url))
        val url = info.url
        val httpMethod = info.httpMethod

        if (!configService.networkBehavior.isUrlEnabled(url)) {
            return null
        }

        // Get the domain, if it can be successfully parsed. If not, don't log this call.
        val domain = getDomain(
            stripUrl(url),
        ) ?: return null

        return captureTelemetry(
            inputValidation = { domainCountLimiter.canLogNetworkRequest(domain) },
            invalidInputCallback = {
                telemetryService.trackAppliedLimit("network_request", AppliedLimitType.DROP)
            },
        ) {
            val spanToken = destination.startSpanCapture(
                schemaType = SchemaType.NetworkRequest(requestStartAttributes(url, httpMethod)),
                startTimeMs = startData.sdkClockStartTime,
                name = getNetworkSpanName(httpMethod, url),
                parentSpanId = startData.traceparent?.getSpanIdFromTraceparent(),
            )

            spanToken.asW3cTraceparent()?.also { traceparent ->
                if (configService.networkSpanForwardingBehavior.shouldForwardForDomain(domain)) {
                    spanToken.setSystemAttribute(EmbNetworkRequestAttributes.EMB_W3C_TRACEPARENT, traceparent)
                    spanToken.setSystemAttribute(EmbNetworkRequestAttributes.EMB_FORWARD_TELEMETRY, "true")
                }
                activeRequests[traceparent] = ActiveRequest(spanToken, httpMethod)
            }
        }
    }

    override fun endRequest(endData: RequestEndData) {
        activeRequests.remove(endData.id)?.let { activeRequest ->
            // The final url is only known when the request ends (e.g. it may be overridden during
            // execution), so apply the modifiers again to the end url. The underlying HTTP request
            // is not affected.
            val modifiedUrl = httpRequestInfoModifierChain.apply(
                MutableHttpRequestInfoImpl(activeRequest.httpMethod, endData.url),
            ).url
            with(activeRequest.spanToken) {
                val statusCode = endData.statusCode
                val errorCode = if (statusCode == null || statusCode <= 0 || statusCode >= 400) {
                    ErrorCodeAttribute.Failure
                } else {
                    null
                }
                requestEndAttributes(endData, modifiedUrl).forEach {
                    setSystemAttribute(it.key, it.value)
                }
                stop(endData.sdkClockEndTime, errorCode)
            }
        }
    }

    private fun generateSchemaAttributes(
        request: HttpNetworkRequest,
        url: String,
        httpMethod: String,
    ): Map<String, String> = mapOf(
        UrlAttributes.URL_FULL to stripUrl(url),
        HttpAttributes.HTTP_REQUEST_METHOD to httpMethod,
        HttpAttributes.HTTP_RESPONSE_STATUS_CODE to request.statusCode,
        HttpAttributes.HTTP_REQUEST_BODY_SIZE to request.bytesSent,
        HttpAttributes.HTTP_RESPONSE_BODY_SIZE to request.bytesReceived,
        ErrorAttributes.ERROR_TYPE to request.errorType,
        ExceptionAttributes.EXCEPTION_MESSAGE to request.errorMessage,
        EmbNetworkRequestAttributes.EMB_W3C_TRACEPARENT to request.w3cTraceparent,
        EmbNetworkRequestAttributes.EMB_FORWARD_TELEMETRY to request.w3cTraceparent?.let { "true" },
        EmbNetworkRequestAttributes.EMB_TRACE_ID to getValidTraceId(request.traceId),
    ).toNonNullMap().mapValues { it.value.toString() }

    private fun requestStartAttributes(url: String, httpMethod: String): Map<String, String> = mapOf(
        UrlAttributes.URL_FULL to stripUrl(url),
        HttpAttributes.HTTP_REQUEST_METHOD to httpMethod,
    ).toNonNullMap().mapValues { it.value }

    private fun requestEndAttributes(endData: RequestEndData, url: String): Map<String, String> = mapOf(
        UrlAttributes.URL_FULL to stripUrl(url),
        HttpAttributes.HTTP_RESPONSE_STATUS_CODE to endData.statusCode,
        HttpAttributes.HTTP_REQUEST_BODY_SIZE to endData.bytesSent,
        HttpAttributes.HTTP_RESPONSE_BODY_SIZE to endData.bytesReceived,
        ErrorAttributes.ERROR_TYPE to endData.errorType,
        ExceptionAttributes.EXCEPTION_MESSAGE to endData.errorMessage,
        UserAgentAttributes.USER_AGENT_NAME to endData.userAgentName,
        UserAgentAttributes.USER_AGENT_VERSION to endData.userAgentVersion,
        EmbNetworkRequestAttributes.EMB_TRACE_ID to getValidTraceId(endData.traceId),
    ).toNonNullMap().mapValues { it.value.toString() }

    private fun getNetworkSpanName(httpMethod: String, url: String) = "$httpMethod ${getUrlPath(stripUrl(url))}"

    /**
     * Returns the span-id of this string if it is a valid W3C traceparent, or null if it is not.
     */
    private fun String.getSpanIdFromTraceparent(): String? = SPAN_ID_FROM_TRACEPARENT_REGEX.matchEntire(this)?.groupValues?.get(1)

    /**
     * Tracks an in-flight request span. [httpMethod] is the post-modifier method captured at request
     * start, retained so the modifiers can be re-applied to the final url when the request ends.
     */
    private class ActiveRequest(
        val spanToken: SpanToken,
        val httpMethod: String,
    )

    private companion object {
        // version(2)-traceId(32)-spanId(16)-flags(2), lowercase hex per the W3C traceparent spec.
        private val SPAN_ID_FROM_TRACEPARENT_REGEX = Regex("[0-9a-f]{2}-[0-9a-f]{32}-([0-9a-f]{16})-[0-9a-f]{2}")
    }
}
