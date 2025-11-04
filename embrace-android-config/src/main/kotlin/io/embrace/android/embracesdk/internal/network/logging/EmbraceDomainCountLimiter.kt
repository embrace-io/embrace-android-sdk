package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.embrace.android.embracesdk.internal.utils.NetworkUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class EmbraceDomainCountLimiter(
    private val defaultLimitSupplier: () -> Int,
    private val domainLimitsSupplier: () -> Map<String, Int>,
) : MemoryCleanerListener, DomainCountLimiter {

    private val domainSetting = ConcurrentHashMap<String, DomainSettings>()
    private val callsPerDomainSuffix = ConcurrentHashMap<String, DomainCount>()
    private val ipAddressNetworkCallCount = AtomicInteger(0)
    private val untrackedNetworkCallCount = AtomicInteger(0)
    private var defaultPerDomainSuffixCallLimit = defaultLimitSupplier()
    private var domainSuffixCallLimits = domainLimitsSupplier()

    private val lock = Any()

    override fun canLogNetworkRequest(domain: String): Boolean {
        synchronized(lock) {
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
                    countPerSuffix = DomainCount(0, limit)
                }

                // Track the number of calls for each domain (or configured suffix)
                suffix?.let {
                    callsPerDomainSuffix[it] = DomainCount(countPerSuffix.requestCount + 1, limit)
                }

                // Exclude if the network call exceeds the limit
                if (countPerSuffix.requestCount < limit) {
                    return true
                }
            }
            return false
        }
    }

    private fun createLimitForDomain(domain: String) {
        runCatching {
            for ((key, value) in domainSuffixCallLimits) {
                if (domain.endsWith(key)) {
                    domainSetting[domain] = DomainSettings(value, key)
                }
            }

            if (!domainSetting.containsKey(domain)) {
                domainSetting[domain] = DomainSettings(defaultPerDomainSuffixCallLimit, domain)
            }
        }
    }

    override fun cleanCollections() {
        synchronized(lock) {
            clearNetworkCalls()
            // re-fetch limits in case they changed since they last time they were fetched
            defaultPerDomainSuffixCallLimit = defaultLimitSupplier()
            domainSuffixCallLimits = domainLimitsSupplier()
        }
    }

    private fun clearNetworkCalls() {
        domainSetting.clear()
        callsPerDomainSuffix.clear()
        ipAddressNetworkCallCount.set(0)
        untrackedNetworkCallCount.set(0)
    }
}
