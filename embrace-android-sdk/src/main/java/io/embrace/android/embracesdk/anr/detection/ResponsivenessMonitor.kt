package io.embrace.android.embracesdk.anr.detection

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.payload.ResponsivenessOutlier
import io.embrace.android.embracesdk.payload.ResponsivenessSnapshot
import java.util.concurrent.atomic.AtomicLong

/**
 * Enables the monitoring of a component or thread's responsiveness by bucketing the time lapsed between calls of [ping] into the specified
 * durational buckets separated by the given boundaries. The counts of the buckets are provided along with start and end times of the
 * first N outliers, defined by any interval greater than [outlierThreshold], where N specified by [outliersLimit].
 *
 * The bucket name is the boundary value (inclusive) down to the next boundary value or 0. The last bucket is anything greater than the
 * last boundary and less than [Long.MAX_VALUE].
 *
 * Note that this does not handle backpressure so don't use it if the rate of pings is expected to exceed the rate at which the pings can
 * be processed sequentially.
 */
internal class ResponsivenessMonitor(
    private val clock: Clock,
    private val name: String,
    private val outliersLimit: Int = defaultOutlierLimit,
    private val outlierThreshold: Long = defaultOutlierThreshold,
) {
    private val outliers = mutableListOf<ResponsivenessOutlier>()
    private val gaps: MutableMap<Bucket, Long> = Bucket.values().associateWith { 0L } as MutableMap<Bucket, Long>
    private val firstPing = AtomicLong(unsetPingTime)
    private val latestPing = AtomicLong(unsetPingTime)
    private val errors = AtomicLong(0)

    @Synchronized
    fun ping() {
        val previousPing = latestPing.getAndSet(clock.now())
        if (previousPing != unsetPingTime) {
            if (!recordGap(latestPing.get(), previousPing)) {
                errors.incrementAndGet()
            }
        } else {
            firstPing.set(latestPing.get())
        }
    }

    @Synchronized
    fun snapshot(): ResponsivenessSnapshot = ResponsivenessSnapshot(
        name = name,
        firstPing = firstPing.get(),
        lastPing = latestPing.get(),
        gaps = gaps.mapKeys { it.key.name },
        outliers = outliers.toList(),
        errors = errors.get()
    )

    @Synchronized
    fun reset() {
        firstPing.set(unsetPingTime)
        latestPing.set(unsetPingTime)
        gaps.putAll(gaps.keys.associateWith { 0L })
        outliers.clear()
        errors.set(0)
    }

    /**
     * Record the duration of the gap between the current ping time and the previous one and return true if it's successful.
     * This method will loop through the bucket boundaries starting from the lowest to find one that is strictly greater than the
     * gap and increment the associated gap bucket. If the gap exceeds the outlier definition and we haven't reached our limit yet,
     * include it in the outliers list.
     *
     * This method will return false in the following situation:
     *  - the gap is negative (the clock should be locked so we don't expect any "time travelling"
     *  - the bucket cannot be found (all valid buckets should be initialized to 0 so this shouldn't happen)
     *  - the gap is greater than or equal to all boundaries, which shouldn't happen as [Long.MAX_VALUE] is the final boundary
     */
    private fun recordGap(currentPing: Long, previousPing: Long): Boolean {
        val gap = currentPing - previousPing
        if (gap >= 0) {
            Bucket.values().find { bucketCandidate ->
                bucketCandidate.max > gap
            }?.let { bucket ->
                if (gaps.containsKey(bucket)) {
                    gaps[bucket] = gaps[bucket]?.inc() ?: 1
                    if (bucket.max > outlierThreshold && outliers.size < outliersLimit) {
                        outliers.add(ResponsivenessOutlier(previousPing, currentPing))
                    }
                    return true
                }
            }
        }

        return false
    }

    enum class Bucket(val max: Long) {
        B1(120L),
        B2(250L),
        B3(500L),
        B4(1000L),
        B5(2000L),
        B6(Long.MAX_VALUE)
    }

    companion object {
        const val defaultOutlierLimit = 100
        const val defaultOutlierThreshold = 500L
        private const val unsetPingTime = -1L
    }
}
