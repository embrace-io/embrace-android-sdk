package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/**
 * Two macrobenchmark runs set against each other, as `compareIterations` reads them.
 *
 * @param baselinePath the directory the run being measured against was read from.
 * @param candidatePath the directory the run under test was read from.
 * @param baselineOnly benchmarks the baseline ran and the candidate did not, sorted.
 * @param candidateOnly benchmarks the candidate ran and the baseline did not, sorted.
 */
@Serializable
internal data class ComparisonReport(
    val baselinePath: String,
    val candidatePath: String,
    val benchmarks: List<BenchmarkComparison>,
    val baselineOnly: List<String>,
    val candidateOnly: List<String>,
)
