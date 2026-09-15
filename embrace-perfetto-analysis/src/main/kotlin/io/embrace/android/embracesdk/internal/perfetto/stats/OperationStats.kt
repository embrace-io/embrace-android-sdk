package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/**
 * What one section cost on one thread, across every occurrence recorded there. A section that ran on
 * two threads is two of these.
 *
 * @param name the section, as atrace recorded it.
 * @param tid the thread that ran these occurrences.
 * @param threadName what the trace called [tid], or null when it named no thread with that id.
 * @param count how many times the section ran on this thread.
 * @param sumNanos the time every occurrence took together.
 * @param traceWindowPercent what share of the whole trace window [sumNanos] accounts for. That window is the
 * capture's first ftrace event to its last. The value does not necessarily sum to 100 and is an approximation
 * @param minNanos the shortest occurrence.
 * @param maxNanos the longest occurrence.
 * @param meanNanos [sumNanos] over [count].
 * @param stdevNanos the population deviation, not the sample one. Zero when the section ran once.
 * @param percentiles the distribution at [DEFAULT_PERCENTILES], in ascending rank.
 */
@Serializable
internal data class OperationStats(
    val name: String,
    val tid: Int,
    val threadName: String?,
    val count: Int,
    val sumNanos: Long,
    val traceWindowPercent: Double,
    val minNanos: Long,
    val maxNanos: Long,
    val meanNanos: Double,
    val stdevNanos: Double,
    val percentiles: List<Percentile>,
)
