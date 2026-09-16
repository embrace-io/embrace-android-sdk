package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/**
 * What one section cost per iteration, across the iterations of one benchmark.
 *
 * @param iterations how many iterations recorded the section.
 * @param occurrences every occurrence, across those iterations and every thread.
 * @param meanOccurrences [occurrences] over [iterations].
 * @param traceWindowPercent the mean of each iteration's share of its own window (approximation).
 * @param minNanos from here down, taken over per-iteration totals rather than occurrences.
 * @param stdevNanos the population deviation, zero when one iteration recorded the section.
 * @param variationPercent [stdevNanos] over [meanNanos] as a percentage, which says how repeatable
 * the measurement was. Zero when the mean is.
 * @param values each recording iteration's total, in iteration order.
 */
@Serializable
internal data class AggregateOperationStats(
    val name: String,
    val iterations: Int,
    val occurrences: Int,
    val meanOccurrences: Double,
    val traceWindowPercent: Double,
    val minNanos: Long,
    val maxNanos: Long,
    val meanNanos: Double,
    val stdevNanos: Double,
    val variationPercent: Double,
    val percentiles: List<Percentile>,
    val values: List<IterationValue>,
)
