package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.CacheableValue
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.logging.EmbraceNetworkCaptureService.Companion.NETWORK_ERROR_CODE
import io.embrace.android.embracesdk.payload.NetworkCallV2
import io.embrace.android.embracesdk.payload.NetworkSessionV2
import io.embrace.android.embracesdk.payload.NetworkSessionV2.DomainCount
import io.embrace.android.embracesdk.session.MemoryCleanerListener
import io.embrace.android.embracesdk.utils.NetworkUtils.getDomain
import io.embrace.android.embracesdk.utils.NetworkUtils.getValidTraceId
import io.embrace.android.embracesdk.utils.NetworkUtils.isIpAddress
import io.embrace.android.embracesdk.utils.NetworkUtils.stripUrl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

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
    private val networkCaptureService: NetworkCaptureService
) : NetworkLoggingService, MemoryCleanerListener {

    private val callsStorageLastUpdate = AtomicInteger(0)

    /**
     * Network calls per domain prepared for the session.
     */
    private val sessionNetworkCalls = ConcurrentHashMap<String, NetworkCallV2>()

    private val networkCallCache = CacheableValue<List<NetworkCallV2>> { callsStorageLastUpdate.get() }

    private val domainSetting = ConcurrentHashMap<String, DomainSettings>()

    private val callsPerDomainSuffix = hashMapOf<String, DomainCount>()

    private val ipAddressNetworkCallCount = AtomicInteger(0)

    private val untrackedNetworkCallCount = AtomicInteger(0)

    private var defaultPerDomainSuffixCallLimit = configService.networkBehavior.getNetworkCaptureLimit()

    private var domainSuffixCallLimits = configService.networkBehavior.getNetworkCallLimitsPerDomainSuffix()

    override fun getNetworkCallsForSession(): NetworkSessionV2 {
        var storedCallsSize: Int? = null
        var cachedCallsSize: Int? = null

        try {
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

    override fun logNetworkCall(
        callId: String,
        url: String,
        httpMethod: String,
        statusCode: Int,
        startTime: Long,
        endTime: Long,
        bytesSent: Long,
        bytesReceived: Long,
        traceId: String?,
        w3cTraceparent: String?,
        networkCaptureData: NetworkCaptureData?
    ) {
        val duration = max(endTime - startTime, 0)
        val strippedUrl = stripUrl(url)
        val validTraceId = getValidTraceId(traceId)
        val networkCall = NetworkCallV2(
            url = strippedUrl,
            httpMethod = httpMethod,
            responseCode = statusCode,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            traceId = validTraceId,
            w3cTraceparent = w3cTraceparent
        )

        if (networkCaptureData != null) {
            networkCaptureService.logNetworkCapturedData(
                url,
                httpMethod,
                statusCode,
                startTime,
                endTime,
                networkCaptureData
            )
        }

        processNetworkCall(callId, networkCall)
    }

    override fun logNetworkError(
        callId: String,
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        errorType: String?,
        errorMessage: String?,
        traceId: String?,
        w3cTraceparent: String?,
        networkCaptureData: NetworkCaptureData?
    ) {
        val duration = max(endTime - startTime, 0)
        val strippedUrl = stripUrl(url)
        val validTraceId = getValidTraceId(traceId)
        val networkCall = NetworkCallV2(
            url = strippedUrl,
            httpMethod = httpMethod,
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            traceId = validTraceId,
            w3cTraceparent = w3cTraceparent,
            errorMessage = errorMessage,
            errorType = errorType
        )

        if (networkCaptureData != null) {
            networkCaptureService.logNetworkCapturedData(
                url,
                httpMethod,
                NETWORK_ERROR_CODE,
                startTime,
                endTime,
                networkCaptureData,
                errorMessage
            )
        }
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
                    storeNetworkCall(callId, networkCall)
                }
                return
            } else if (!domainSetting.containsKey(domain)) {
                createLimitForDomain(domain)
            }

            val settings = domainSetting[domain]
            if (settings == null) {
                // Not sure how this is possible, but in case it is, limit logged logs where we can't figure out the settings to apply
                if (untrackedNetworkCallCount.getAndIncrement() < defaultPerDomainSuffixCallLimit) {
                    storeNetworkCall(callId, networkCall)
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
                    storeNetworkCall(callId, networkCall)
                }

                // Track the number of calls for each domain (or configured suffix)
                suffix?.let {
                    callsPerDomainSuffix[it] = DomainCount(countPerSuffix.requestCount + 1, limit)
                    logger.logDeveloper(
                        "EmbraceNetworkLoggingService",
                        "Call per domain $domain ${countPerSuffix.requestCount + 1}"
                    )
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

    private fun storeNetworkCall(callId: String, networkCall: NetworkCallV2) {
        callsStorageLastUpdate.incrementAndGet()
        sessionNetworkCalls[callId] = networkCall
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
