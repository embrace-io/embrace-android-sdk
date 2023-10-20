package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.CacheableValue
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.http.NetworkCaptureData
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

    private val domainSettings = ConcurrentHashMap<String, DomainSettings>()

    private val callsPerDomain = hashMapOf<String, DomainCount>()

    private val ipAddressCount = AtomicInteger(0)

    override fun getNetworkCallsForSession(): NetworkSessionV2 {
        val calls = networkCallCache.value {
            synchronized(callsStorageLastUpdate) {
                sessionNetworkCalls.values.toList()
            }
        }

        val storedCallsSize = sessionNetworkCalls.size
        val cachedCallsSize = calls.size

        val overLimit = hashMapOf<String, DomainCount>()
        for ((key, value) in callsPerDomain) {
            if (value.requestCount > value.captureLimit) {
                overLimit[key] = value
            }
        }

        if (cachedCallsSize != storedCallsSize) {
            val msg = "Cached network call count different than expected: $cachedCallsSize instead of $storedCallsSize"
            logger.logError(msg, IllegalStateException(msg), true)
        }

        // clear calls per domain and session network calls lists before be used by the next session
        callsPerDomain.clear()
        return NetworkSessionV2(calls, overLimit)
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
        storeSettings(url)
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
        storeSettings(url)
    }

    /**
     * Process network calls to be ready when the session requests them.
     *
     * @param callId      the unique ID that identifies the specific network call instance being recorded
     * @param networkCall that is going to be captured
     */
    private fun processNetworkCall(callId: String, networkCall: NetworkCallV2) {
        // Get the domain, if it can be successfully parsed
        val domain = networkCall.url?.let {
            getDomain(it)
        }

        if (domain == null) {
            logger.logDeveloper("EmbraceNetworkLoggingService", "Domain is not present")
            return
        }

        logger.logDeveloper("EmbraceNetworkLoggingService", "Domain: $domain")

        if (isIpAddress(domain)) {
            logger.logDeveloper("EmbraceNetworkLoggingService", "Domain is an ip address")
            val captureLimit = configService.networkBehavior.getNetworkCaptureLimit()

            if (ipAddressCount.getAndIncrement() < captureLimit) {
                // only capture if the ipAddressCount has not exceeded defaultLimit
                logger.logDeveloper("EmbraceNetworkLoggingService", "capturing network call")
                storeNetworkCall(callId, networkCall)
            } else {
                logger.logDeveloper("EmbraceNetworkLoggingService", "capture limit exceeded")
            }
            return
        }

        val settings = domainSettings[domain]
        if (settings == null) {
            logger.logDeveloper("EmbraceNetworkLoggingService", "no domain settings")
            storeNetworkCall(callId, networkCall)
        } else {
            val suffix = settings.suffix
            val limit = settings.limit
            var count = callsPerDomain[suffix]

            if (count == null) {
                count = DomainCount(1, limit)
            }

            // Exclude if the network call exceeds the limit
            if (count.requestCount < limit) {
                storeNetworkCall(callId, networkCall)
            } else {
                logger.logDeveloper("EmbraceNetworkLoggingService", "capture limit exceeded")
            }

            // Track the number of calls for each domain (or configured suffix)
            suffix?.let {
                callsPerDomain[it] = DomainCount(count.requestCount + 1, limit)
                logger.logDeveloper(
                    "EmbraceNetworkLoggingService",
                    "Call per domain $domain ${count.requestCount + 1}"
                )
            }
        }
    }

    private fun storeSettings(url: String) {
        try {
            val mergedLimits = configService.networkBehavior.getNetworkCallLimitsPerDomain()

            val domain = getDomain(url)
            if (domain == null) {
                logger.logDeveloper("EmbraceNetworkLoggingService", "Domain not present")
                return
            }
            if (domainSettings.containsKey(domain)) {
                logger.logDeveloper("EmbraceNetworkLoggingService", "No settings for $domain")
                return
            }

            for ((key, value) in mergedLimits) {
                if (domain.endsWith(key)) {
                    domainSettings[domain] = DomainSettings(value, key)
                    return
                }
            }

            val defaultLimit = configService.networkBehavior.getNetworkCaptureLimit()
            domainSettings[domain] = DomainSettings(defaultLimit, domain)
        } catch (ex: Exception) {
            logger.logDebug("Failed to determine limits for URL: $url", ex)
        }
    }

    private fun storeNetworkCall(callId: String, networkCall: NetworkCallV2) {
        synchronized(callsStorageLastUpdate) {
            callsStorageLastUpdate.incrementAndGet()
            sessionNetworkCalls[callId] = networkCall
        }
    }

    private fun clearNetworkCalls() {
        synchronized(callsStorageLastUpdate) {
            callsStorageLastUpdate.set(0)
            sessionNetworkCalls.clear()
        }
    }

    override fun cleanCollections() {
        domainSettings.clear()
        callsPerDomain.clear()
        clearNetworkCalls()
        // reset counters
        ipAddressCount.set(0)
        logger.logDeveloper("EmbraceNetworkLoggingService", "Collections cleaned")
    }
}
