package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.logging.EmbraceNetworkCaptureService.Companion.NETWORK_ERROR_CODE
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
    private val networkLoggingDomainCountLimiter: NetworkLoggingDomainCountLimiter,
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
                networkRequest.url, //TODO: This uses the non-stripped URL, is that correct?
                networkRequest.httpMethod,
                networkRequest.responseCode ?: NETWORK_ERROR_CODE,
                networkRequest.startTime,
                networkRequest.endTime,
                networkRequest.networkCaptureData,
                networkRequest.errorMessage
            )
        }
    }

    override fun logURLConnectionNetworkRequest(callId: String, request: EmbraceNetworkRequest) {
        logNetworkRequest(request)
    }

    /**
     * Records network calls as spans if their domain can be parsed and is within the limits.
     *
     * @param networkCall that is going to be captured
     */
    private fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        // TODO: Shouldn't we ignore domains that can't be parsed for networkCapturedData too?
        // Shouldn't we also track limits for networkCapturedData?

        // Get the domain, if it can be successfully parsed. If not, don't log this call.
        val domain = getDomain(
            stripUrl(networkRequest.url)
        ) ?: return

        if (networkLoggingDomainCountLimiter.canLogNetworkRequest(domain)) {
            //TODO: Why do we want to strip the URL?
            val strippedUrl = stripUrl(networkRequest.url)

            val networkRequestSchemaType = SchemaType.NetworkRequest(networkRequest)
            spanService.recordCompletedSpan(
                name = "${networkRequest.httpMethod} ${getUrlPath(strippedUrl)}",
                startTimeMs = networkRequest.startTime,
                endTimeMs = networkRequest.endTime,
                errorCode = null,
                parent = null,
                attributes = networkRequestSchemaType.attributes(),
                type = EmbType.Performance.Network,
            )
        }
    }

}
