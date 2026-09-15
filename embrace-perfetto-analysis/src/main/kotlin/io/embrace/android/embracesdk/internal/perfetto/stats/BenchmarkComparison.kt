package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/**
 * One benchmark both runs ran, section by section.
 *
 * @param baselineIterations how many iterations of it the baseline ran, which need not match the
 * candidate: a comparison of means holds either way, though fewer iterations mean a noisier one.
 * @param slower how many of [operations] moved and cost more in the candidate.
 * @param faster how many of [operations] moved and cost less. Sections within the noise are neither.
 * @param baselineOnly sections and counters only the baseline recorded, sorted. A section that has
 * gone is as much a difference as one that got slower, but it has no delta to report.
 * @param candidateOnly sections and counters only the candidate recorded, sorted.
 */
@Serializable
internal data class BenchmarkComparison(
    val benchmark: String,
    val baselineIterations: Int,
    val candidateIterations: Int,
    val slower: Int,
    val faster: Int,
    val operations: List<OperationComparison>,
    val counters: List<CounterComparison>,
    val baselineOnly: List<String>,
    val candidateOnly: List<String>,
)
