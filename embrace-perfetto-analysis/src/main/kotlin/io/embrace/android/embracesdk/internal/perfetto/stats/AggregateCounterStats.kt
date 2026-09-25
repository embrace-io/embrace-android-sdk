package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/**
 * What one counter tallied per iteration, across the iterations of one benchmark.
 *
 * @param sampleCount how many values those iterations recorded between them.
 * @param minTotal from here down, taken over per-iteration totals.
 * @param values each recording iteration's total, as [AggregateOperationStats.values].
 */
@Serializable
internal data class AggregateCounterStats(
    val name: String,
    val iterations: Int,
    val sampleCount: Int,
    val meanSamples: Double,
    val minTotal: Long,
    val maxTotal: Long,
    val meanTotal: Double,
    val sumTotal: Long,
    val values: List<IterationValue>,
)
