package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/**
 * One benchmark of a run, reduced to what its iterations agree on.
 */
@Serializable
internal data class BenchmarkStats(
    val benchmark: String,
    val iterations: List<IterationSummary>,
    val operations: List<AggregateOperationStats>,
    val counters: List<AggregateCounterStats>,
    val partial: List<String>,
    val missing: List<String>,
)
