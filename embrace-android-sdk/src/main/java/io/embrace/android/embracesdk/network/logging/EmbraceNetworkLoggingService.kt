package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.logging.EmbraceNetworkCaptureService.Companion.NETWORK_ERROR_CODE
import io.embrace.android.embracesdk.payload.NetworkSessionV2.DomainCount
import io.embrace.android.embracesdk.session.MemoryCleanerListener
import io.embrace.android.embracesdk.utils.NetworkUtils.getDomain
import io.embrace.android.embracesdk.utils.NetworkUtils.getUrlPath
import io.embrace.android.embracesdk.utils.NetworkUtils.isIpAddress
import io.embrace.android.embracesdk.utils.NetworkUtils.stripUrl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Logs network calls according to defined limits per domain.
 *
 *
 * Limits can be defined either in server-side configuration or within the embrace configuration file.
 * A limit of 0 disables logging for the domain. All network calls are captured up to the limit,
 * and the number of calls is also captured if the limit is exceeded.
 */
internal class EmbraceNetworkLoggingService(
    private val configService: ConfigService,
    private val logger: InternalEmbraceLogger,
    private val networkCaptureService: NetworkCaptureService,
    private val spanService: SpanService
) : NetworkLoggingService, MemoryCleanerListener {

    private val callsStorageLastUpdate = AtomicInteger(0)

    private val domainSetting = ConcurrentHashMap<String, DomainSettings>()

    private val callsPerDomainSuffix = hashMapOf<String, DomainCount>()

    private val ipAddressNetworkCallCount = AtomicInteger(0)

    private val untrackedNetworkCallCount = AtomicInteger(0)

    private var defaultPerDomainSuffixCallLimit = configService.networkBehavior.getNetworkCaptureLimit()

    private var domainSuffixCallLimits = configService.networkBehavior.getNetworkCallLimitsPerDomainSuffix()

    override fun logNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        logNetworkCaptureData(networkRequest)
        processNetworkRequest(networkRequest)
    }

    private fun logNetworkCaptureData(networkRequest: EmbraceNetworkRequest) {
        if (networkRequest.networkCaptureData != null) {
            networkCaptureService.logNetworkCapturedData(
                networkRequest.url, //TODO: This used the non-stripped URL, is that correct?
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
     * Process network calls to assert that no limits are exceeded.
     *
     * @param networkRequest the network request to process
     */
    private fun processNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        // Get the domain, if it can be successfully parsed. If not, don't log this call.
        val domain = getDomain(
            stripUrl(networkRequest.url)
        ) ?: return

        synchronized(callsStorageLastUpdate) {
            if (isIpAddress(domain)) {
                if (ipAddressNetworkCallCount.getAndIncrement() < defaultPerDomainSuffixCallLimit) {
                    storeNetworkRequest(networkRequest)
                }
                return
            } else if (!domainSetting.containsKey(domain)) {
                createLimitForDomain(domain)
            }

            val settings = domainSetting[domain]
            if (settings == null) {
                // Not sure how this is possible, but in case it is, limit logged logs where we can't figure out the settings to apply
                if (untrackedNetworkCallCount.getAndIncrement() < defaultPerDomainSuffixCallLimit) {
                    storeNetworkRequest(networkRequest)
                }
                return
            } else {
                val suffix = settings.suffix
                val limit = settings.limit
                var countPerSuffix = callsPerDomainSuffix[suffix]

                if (countPerSuffix == null) {
                    countPerSuffix = DomainCount(0, limit)
                }

                // Exclude if the network call exceeds the limit
                if (countPerSuffix.requestCount < limit) {
                    storeNetworkRequest(networkRequest)
                }

                // Track the number of calls for each domain (or configured suffix)
                suffix?.let {
                    callsPerDomainSuffix[it] = DomainCount(countPerSuffix.requestCount + 1, limit)
                }
            }
        }
    }

    private fun createLimitForDomain(domain: String) {
        try {
            for ((key, value) in domainSuffixCallLimits) {
                if (domain.endsWith(key)) {
                    domainSetting[domain] = DomainSettings(value, key)
                }
            }

            if (!domainSetting.containsKey(domain)) {
                domainSetting[domain] = DomainSettings(defaultPerDomainSuffixCallLimit, domain)
            }
        } catch (ex: Exception) {
            logger.logDebug("Failed to determine limits for domain: $domain", ex)
        }
    }

    private fun storeNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        callsStorageLastUpdate.incrementAndGet()

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

    override fun cleanCollections() {
        clearNetworkCalls()
        // re-fetch limits in case they changed since they last time they were fetched
        defaultPerDomainSuffixCallLimit = configService.networkBehavior.getNetworkCaptureLimit()
        domainSuffixCallLimits = configService.networkBehavior.getNetworkCallLimitsPerDomainSuffix()
    }

    private fun clearNetworkCalls() {
        synchronized(callsStorageLastUpdate) {
            domainSetting.clear()
            callsPerDomainSuffix.clear()
            ipAddressNetworkCallCount.set(0)
            untrackedNetworkCallCount.set(0)
            callsStorageLastUpdate.set(0)
        }
    }
}
