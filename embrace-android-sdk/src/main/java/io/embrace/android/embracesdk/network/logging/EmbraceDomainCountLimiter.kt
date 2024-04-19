package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.NetworkSessionV2
import io.embrace.android.embracesdk.session.MemoryCleanerListener
import io.embrace.android.embracesdk.utils.NetworkUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class EmbraceDomainCountLimiter(
    private val configService: ConfigService,
    private val logger: InternalEmbraceLogger,
) : MemoryCleanerListener, DomainCountLimiter {

    private val domainSetting = ConcurrentHashMap<String, DomainSettings>()
    private val callsPerDomainSuffix = hashMapOf<String, NetworkSessionV2.DomainCount>()
    private val ipAddressNetworkCallCount = AtomicInteger(0)
    private val untrackedNetworkCallCount = AtomicInteger(0)
    private var defaultPerDomainSuffixCallLimit = configService.networkBehavior.getNetworkCaptureLimit()
    private var domainSuffixCallLimits = configService.networkBehavior.getNetworkCallLimitsPerDomainSuffix()

    override fun canLogNetworkRequest(domain: String): Boolean {
        // TODO: Should we synchronize on another object now that we don't use callsStorageLastUpdate?
        if (NetworkUtils.isIpAddress(domain)) {
            // TODO: All of the IP address domains fall under the same limit? Is that correct?
            return ipAddressNetworkCallCount.getAndIncrement() < defaultPerDomainSuffixCallLimit
        }

        if (!domainSetting.containsKey(domain)) {
            createLimitForDomain(domain)
        }

        val settings = domainSetting[domain]
        if (settings == null) {
            // Not sure how this is possible, but in case it is, limit logged logs where we can't figure out the settings to apply
            return untrackedNetworkCallCount.getAndIncrement() < defaultPerDomainSuffixCallLimit
        } else {
            val suffix = settings.suffix
            val limit = settings.limit
            var countPerSuffix = callsPerDomainSuffix[suffix]

            if (countPerSuffix == null) {
                countPerSuffix = NetworkSessionV2.DomainCount(0, limit)
            }

            // Track the number of calls for each domain (or configured suffix)
            suffix?.let {
                callsPerDomainSuffix[it] = NetworkSessionV2.DomainCount(countPerSuffix.requestCount + 1, limit)
            }

            // Exclude if the network call exceeds the limit
            if (countPerSuffix.requestCount < limit) {
                return true
            }
        }
        return false
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

    override fun cleanCollections() {
        clearNetworkCalls()
        // re-fetch limits in case they changed since they last time they were fetched
        defaultPerDomainSuffixCallLimit = configService.networkBehavior.getNetworkCaptureLimit()
        domainSuffixCallLimits = configService.networkBehavior.getNetworkCallLimitsPerDomainSuffix()
    }

    private fun clearNetworkCalls() {
        domainSetting.clear()
        callsPerDomainSuffix.clear()
        ipAddressNetworkCallCount.set(0)
        untrackedNetworkCallCount.set(0)
    }
}
