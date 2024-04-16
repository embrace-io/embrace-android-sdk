package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.CacheableValue
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.NetworkCallV2
import io.embrace.android.embracesdk.payload.NetworkSessionV2
import io.embrace.android.embracesdk.payload.NetworkSessionV2.DomainCount
import io.embrace.android.embracesdk.session.MemoryCleanerListener
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
import io.embrace.android.embracesdk.utils.NetworkUtils.getDomain
import io.embrace.android.embracesdk.utils.NetworkUtils.getValidTraceId
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

    /**
     * Network calls per domain prepared for the session.
     */
    private val sessionNetworkCalls = ConcurrentHashMap<String, NetworkCallV2>()

    private val networkCallTraces = ConcurrentHashMap<String, PersistableEmbraceSpan>()

    private val networkCallCache = CacheableValue<List<NetworkCallV2>> { callsStorageLastUpdate.get() }

    private val domainSetting = ConcurrentHashMap<String, DomainSettings>()

    private val callsPerDomainSuffix = hashMapOf<String, DomainCount>()

    private val ipAddressNetworkCallCount = AtomicInteger(0)

    private val untrackedNetworkCallCount = AtomicInteger(0)

    private var defaultPerDomainSuffixCallLimit = configService.networkBehavior.getNetworkCaptureLimit()

    private var domainSuffixCallLimits = configService.networkBehavior.getNetworkCallLimitsPerDomainSuffix()

    override fun getNetworkCallsSnapshot(): NetworkSessionV2 {
        var storedCallsSize: Int? = null
        var cachedCallsSize: Int? = null

        try {
            // TODO: Do we need this?
            synchronized(callsStorageLastUpdate) {
                val calls = networkCallCache.value {
                    sessionNetworkCalls.values.toList()
                }

                storedCallsSize = sessionNetworkCalls.size
                cachedCallsSize = calls.size

                val overLimit = hashMapOf<String, DomainCount>()
                for ((key, value) in callsPerDomainSuffix) {
                    if (value.requestCount > value.captureLimit) {
                        overLimit[key] = value
                    }
                }

                return NetworkSessionV2(calls, overLimit)
            }
        } finally {
            if (cachedCallsSize != storedCallsSize) {
                val msg = "Cached network call count different than expected: $cachedCallsSize instead of $storedCallsSize"
                logger.logError(msg, IllegalStateException(msg), true)
            }
        }
    }

    override fun endNetworkRequest(
        callId: String,
        statusCode: Int,
        endTime: Long,
        bytesSent: Long,
        bytesReceived: Long,
        networkCaptureData: NetworkCaptureData?
    ) {
        val networkCall = NetworkCallV2(
            responseCode = statusCode,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
            endTime = endTime,
        )

        if (networkCaptureData != null) {
            // TODO: record network request capture
        }

        endNetworkCall(callId, networkCall)
    }

    override fun endNetworkRequestWithError(
        callId: String,
        endTime: Long,
        errorType: String?,
        errorMessage: String?,
        networkCaptureData: NetworkCaptureData?
    ) {
        val networkCall = NetworkCallV2(
            endTime = endTime,
            errorMessage = errorMessage,
            errorType = errorType
        )

        if (networkCaptureData != null) {
            // TODO: record network request capture
        }

        endNetworkCall(callId, networkCall)
    }

    override fun startNetworkCall(
        callId: String,
        url: String,
        httpMethod: String,
        statusCode: Int,
        startTime: Long,
        traceId: String?,
        w3cTraceparent: String?
    ) {
        val strippedUrl = stripUrl(url)
        val validTraceId = getValidTraceId(traceId)
        val networkCall = NetworkCallV2(
            url = strippedUrl,
            httpMethod = httpMethod,
            responseCode = statusCode,
            startTime = startTime,
            traceId = validTraceId,
            w3cTraceparent = w3cTraceparent
        )
        processNetworkCall(callId, networkCall)
    }

    override fun cleanCollections() {
        clearNetworkCalls()
        // re-fetch limits in case they changed since they last time they were fetched
        defaultPerDomainSuffixCallLimit = configService.networkBehavior.getNetworkCaptureLimit()
        domainSuffixCallLimits = configService.networkBehavior.getNetworkCallLimitsPerDomainSuffix()
    }

    /**
     * Process network calls to be ready when the session requests them.
     *
     * @param callId      the unique ID that identifies the specific network call instance being recorded
     * @param networkCall that is going to be captured
     */
    private fun processNetworkCall(callId: String, networkCall: NetworkCallV2) {
        // Get the domain, if it can be successfully parsed. If not, don't log this call.
        val domain = networkCall.url?.let {
            getDomain(it)
        } ?: return

        synchronized(callsStorageLastUpdate) {
            if (isIpAddress(domain)) {
                if (ipAddressNetworkCallCount.getAndIncrement() < defaultPerDomainSuffixCallLimit) {
                    startNetworkRequestTrace(callId, networkCall)
                }
                return
            } else if (!domainSetting.containsKey(domain)) {
                createLimitForDomain(domain)
            }

            val settings = domainSetting[domain]
            if (settings == null) {
                // Not sure how this is possible, but in case it is, limit logged logs where we can't figure out the settings to apply
                if (untrackedNetworkCallCount.getAndIncrement() < defaultPerDomainSuffixCallLimit) {
                    startNetworkRequestTrace(callId, networkCall)
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
                    startNetworkRequestTrace(callId, networkCall)
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

    private fun startNetworkRequestTrace(callId: String, networkCall: NetworkCallV2) {
        callsStorageLastUpdate.incrementAndGet()
        sessionNetworkCalls[callId] = networkCall
        spanService.startSpan(
            name = "/${networkCall.httpMethod} /${networkCall.url}",
            type = EmbType.Performance.Default,
            internal = false
        )?.let {
            networkCallTraces[callId] = it
        }
    }

    private fun endNetworkCall(callId: String, networkCall: NetworkCallV2) {
        networkCallTraces[callId]?.apply {
            // TODO: figure out the real failure case
            // TODO: add attributes properly
            val errorCode = if (networkCall.responseCode == null || networkCall.responseCode >= 400) {
                addAttribute("error-type", networkCall.errorType ?: "")
                addAttribute("error-message", networkCall.errorMessage ?: "")
                ErrorCode.FAILURE
            } else {
                addAttribute("request-size", networkCall.bytesSent.toString())
                addAttribute("response-size", networkCall.bytesReceived.toString())
                null
            }

            stop(endTimeMs = networkCall.endTime, errorCode = errorCode)
        }
    }

    private fun clearNetworkCalls() {
        synchronized(callsStorageLastUpdate) {
            domainSetting.clear()
            callsPerDomainSuffix.clear()
            ipAddressNetworkCallCount.set(0)
            untrackedNetworkCallCount.set(0)
            callsStorageLastUpdate.set(0)
            sessionNetworkCalls.clear()
        }
    }
}
