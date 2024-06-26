package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.logging.EmbraceNetworkCaptureService.Companion.NETWORK_ERROR_CODE
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.utils.NetworkUtils.getDomain
import io.embrace.android.embracesdk.utils.NetworkUtils.getUrlPath
import io.embrace.android.embracesdk.utils.NetworkUtils.stripUrl

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

            val networkRequestSchemaType = SchemaType.NetworkRequest(networkRequest)
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
}
