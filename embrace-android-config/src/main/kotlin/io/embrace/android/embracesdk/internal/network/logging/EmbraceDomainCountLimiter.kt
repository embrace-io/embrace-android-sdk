package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.internal.utils.NetworkUtils
import io.embrace.android.embracesdk.internal.utils.concurrent.LimitCounter
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the number of requests logged against per-domain/per-host limits. Limits are defined on a closest-match basis,
 * where a limit for `sdk.limited.org` overrides `limited.org` overrides `.org`.
 */
class EmbraceDomainCountLimiter(
    private val defaultLimitSupplier: () -> Int,
    private val domainLimitsSupplier: () -> Map<String, Int>,
) : DomainCountLimiter {

    private class SuffixLimit(val suffix: String, val counter: LimitCounter)

    // configured suffixes with their own counters, ordered most-specific (longest) first
    @Volatile
    private var suffixLimits: List<SuffixLimit> = buildSuffixLimits()

    @Volatile
    private var defaultLimit: Int = defaultLimitSupplier()

    @Volatile
    private var ipLimit: LimitCounter = LimitCounter(defaultLimit)

    // one bucket per domain that matches no configured suffix, created on demand
    private val unconfiguredCounts = ConcurrentHashMap<String, LimitCounter>()

    override fun canLogNetworkRequest(domain: String): Boolean {
        if (NetworkUtils.isIpAddress(domain)) {
            return ipLimit.increment()
        }

        for (rule in suffixLimits) {
            if (domain.endsWith(rule.suffix)) {
                // Limit.increment() atomically claims a slot or reports the bucket full; on full we
                // fall through to the next-broadest matching suffix.
                return rule.counter.increment()
            }
        }

        return bucketForUnconfigured(domain).increment()
    }

    private fun bucketForUnconfigured(domain: String): LimitCounter {
        unconfiguredCounts[domain]?.let { return it }
        val created = LimitCounter(defaultLimit)
        return unconfiguredCounts.putIfAbsent(domain, created) ?: created
    }

    override fun reset() {
        // buildSuffixLimits() first to minimise the time window where the data is not consistent
        val tmpSuffixLimits = buildSuffixLimits()

        // These are not locked, and there is a window during session part changeovers where some limits
        // will apply from the previous session and others will apply from the new session.
        // This may seem like a consistency nightmare, but given that requests are run on worker threads
        // their real ordering is not perfectly consistent with session part handovers. Adding a lock ensures
        // that the domain limit applied to one request is consistent with the limits applied to another
        // concurrent request - it does *not* imply that the limits have a consistent request/session part
        // relationship.
        ipLimit = LimitCounter(defaultLimit)
        defaultLimit = defaultLimitSupplier()
        suffixLimits = tmpSuffixLimits
        unconfiguredCounts.clear()
    }

    private fun buildSuffixLimits(): List<SuffixLimit> =
        domainLimitsSupplier().entries
            .sortedByDescending { it.key.length }
            .map { SuffixLimit(it.key, LimitCounter(it.value)) }
}
