package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlin.math.ceil

/** The ranks every section is measured at. */
internal val DEFAULT_PERCENTILES: List<Int> = listOf(50, 90, 95, 99)

/**
 * The nearest-rank percentile of [sortedNanos], which must already be sorted ascending.
 *
 * The rank counts [rank] times the sample size before dividing by 100, which keeps it exact in
 * floating point.
 */
internal fun percentile(sortedNanos: LongArray, rank: Int): Percentile {
    require(sortedNanos.isNotEmpty()) { "no durations to take the ${rank}th percentile of" }
    val nearest = ceil(rank.toDouble() * sortedNanos.size / 100).toInt().coerceIn(1, sortedNanos.size)
    return Percentile(rank, sortedNanos[nearest - 1])
}
