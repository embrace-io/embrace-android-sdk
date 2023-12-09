package io.embrace.android.embracesdk.anr.detection

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.internal.clock.Clock
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
    private val outliersLimit: Int = 100,
    private val outlierThreshold: Long = 500L,
    boundaries: List<Long> = listOf(120L, 250L, 500L, 2000L)
) {
    private val buckets = boundaries.sorted().plus(Long.MAX_VALUE)
    private val outliers = mutableListOf<Outlier>()
    private val gaps: MutableMap<Long, Long> = buckets.associateWith { 0L } as MutableMap<Long, Long>
    private val firstPing = AtomicLong(unsetPingTime)
    private val latestPing = AtomicLong(unsetPingTime)
    private val errors = AtomicLong(0)

    @Synchronized
    fun ping() {
        val previousPing = latestPing.get()
        latestPing.set(clock.now())
        if (previousPing != unsetPingTime) {
            (latestPing.get() - previousPing).let { gap ->
                if (gap >= 0) {
                    buckets.find { bucketCandidate ->
                        bucketCandidate > gap
                    }?.let { bucket ->
                        gaps[bucket] = gaps[bucket]?.inc() ?: 1L
                        if (bucket > outlierThreshold && outliers.size < outliersLimit) {
                            outliers.add(Outlier(previousPing, latestPing.get()))
                        }
                    } ?: errors.incrementAndGet()
                } else {
                    errors.incrementAndGet()
                }
            }
        } else {
            firstPing.set(latestPing.get())
        }
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        name = name,
        firstPing = firstPing.get(),
        lastPing = latestPing.get(),
        gaps = gaps.toMap(),
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

    data class Snapshot(
        @SerializedName("name")
        val name: String,

        @SerializedName("first")
        val firstPing: Long,

        @SerializedName("last")
        val lastPing: Long,

        @SerializedName("gaps")
        val gaps: Map<Long, Long>,

        @SerializedName("outliers")
        val outliers: List<Outlier>,

        @SerializedName("errors")
        val errors: Long,
    )

    data class Outlier(
        @SerializedName("start")
        val start: Long,

        @SerializedName("end")
        val end: Long
    )

    companion object {
        private const val unsetPingTime = -1L
    }
}
