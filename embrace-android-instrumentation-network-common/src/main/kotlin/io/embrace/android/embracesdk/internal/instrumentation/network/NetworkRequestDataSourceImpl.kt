package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.network.logging.DomainCountLimiter
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getDomain
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getUrlPath
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getValidTraceId
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.stripUrl
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import io.opentelemetry.kotlin.semconv.ErrorAttributes
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.opentelemetry.kotlin.semconv.HttpAttributes
import io.opentelemetry.kotlin.semconv.IncubatingApi
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
    "network_request_data_source"
) {
    private val activeRequests: MutableMap<String, SpanToken> = ConcurrentHashMap()
    private val domainCountLimiter: DomainCountLimiter = args.configService.networkBehavior.domainCountLimiter

    override fun recordNetworkRequest(request: HttpNetworkRequest) {
        if (!configService.networkBehavior.isUrlEnabled(request.url)) {
            return
        }

        // Get the domain, if it can be successfully parsed. If not, don't log this call.
        val domain = getDomain(
            stripUrl(request.url)
        ) ?: return

        captureTelemetry(
            inputValidation = {
                domainCountLimiter.canLogNetworkRequest(domain)
            },
            invalidInputCallback = {
                telemetryService.trackAppliedLimit("network_request", AppliedLimitType.DROP)
            }
        ) {
            val networkRequestSchemaType = SchemaType.NetworkRequest(generateSchemaAttributes(request))
            val statusCode = request.statusCode
            val errorCode = if (statusCode == null || statusCode <= 0 || statusCode >= 400) {
                ErrorCodeAttribute.Failure
            } else {
                null
            }
            recordCompletedSpan(
                name = getNetworkSpanName(request.httpMethod, request.url),
                startTimeMs = request.startTime,
                endTimeMs = request.endTime,
                type = EmbType.Performance.Network,
                attributes = networkRequestSchemaType.attributes(),
                errorCode = errorCode
            )
        }
    }

    override fun startRequest(startData: RequestStartData): String? {
        if (!configService.networkBehavior.isUrlEnabled(startData.url)) {
            return null
        }

        // Get the domain, if it can be successfully parsed. If not, don't log this call.
        val domain = getDomain(
            stripUrl(startData.url)
        ) ?: return null

        return captureTelemetry(
            inputValidation = { domainCountLimiter.canLogNetworkRequest(domain) },
            invalidInputCallback = {
                telemetryService.trackAppliedLimit("network_request", AppliedLimitType.DROP)
            }
        ) {
            val spanToken = destination.startSpanCapture(
                schemaType = SchemaType.NetworkRequest(requestStartAttributes(startData)),
                startTimeMs = startData.sdkClockStartTime,
                name = getNetworkSpanName(startData.httpMethod, startData.url)
            )

            spanToken.asW3cTraceparent()?.also { traceparent ->
                if (configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled()) {
                    spanToken.setSystemAttribute("emb.w3c_traceparent", traceparent)
                }
                activeRequests[traceparent] = spanToken
            }
        }
    }

    @OptIn(IncubatingApi::class)
    override fun endRequest(endData: RequestEndData) {
        activeRequests.remove(endData.id)?.apply {
            val statusCode = endData.statusCode
            val errorCode = if (statusCode == null || statusCode <= 0 || statusCode >= 400) {
                ErrorCodeAttribute.Failure
            } else {
                null
            }
            requestEndAttributes(endData).forEach {
                setSystemAttribute(it.key, it.value)
            }
            stop(endData.sdkClockEndTime, errorCode)
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

    @OptIn(IncubatingApi::class)
    private fun requestStartAttributes(startData: RequestStartData): Map<String, String> = mapOf(
        "url.full" to stripUrl(startData.url),
        HttpAttributes.HTTP_REQUEST_METHOD to startData.httpMethod,
    ).toNonNullMap().mapValues { it.value }

    @OptIn(IncubatingApi::class)
    private fun requestEndAttributes(endData: RequestEndData): Map<String, String> = mapOf(
        "url.full" to stripUrl(endData.url),
        HttpAttributes.HTTP_RESPONSE_STATUS_CODE to endData.statusCode,
        HttpAttributes.HTTP_REQUEST_BODY_SIZE to endData.bytesSent,
        HttpAttributes.HTTP_RESPONSE_BODY_SIZE to endData.bytesReceived,
        ErrorAttributes.ERROR_TYPE to endData.errorType,
        ExceptionAttributes.EXCEPTION_MESSAGE to endData.errorMessage,
        "emb.trace_id" to getValidTraceId(endData.traceId),
    ).toNonNullMap().mapValues { it.value.toString() }

    private fun getNetworkSpanName(httpMethod: String, url: String) = "$httpMethod ${getUrlPath(stripUrl(url))}"
}
