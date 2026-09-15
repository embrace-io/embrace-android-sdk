package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.BenchmarkComparison
import io.embrace.android.embracesdk.internal.perfetto.stats.ComparisonReport
import io.embrace.android.embracesdk.internal.perfetto.stats.CounterComparison
import io.embrace.android.embracesdk.internal.perfetto.stats.OperationComparison

internal const val BASELINE_RUN = "perf/macrobenchmark/baseline"
internal const val CANDIDATE_RUN = "perf/macrobenchmark/candidate"

internal fun comparisonReport(
    benchmarks: List<BenchmarkComparison> = listOf(benchmarkComparison()),
    baselineOnly: List<String> = emptyList(),
    candidateOnly: List<String> = emptyList(),
) = ComparisonReport(
    baselinePath = BASELINE_RUN,
    candidatePath = CANDIDATE_RUN,
    benchmarks = benchmarks,
    baselineOnly = baselineOnly,
    candidateOnly = candidateOnly,
)

internal fun benchmarkComparison(
    benchmark: String = SESSION,
    operations: List<OperationComparison> = listOf(operationComparison()),
    counters: List<CounterComparison> = emptyList(),
    slower: Int = 1,
    faster: Int = 0,
    baselineOnly: List<String> = emptyList(),
    candidateOnly: List<String> = emptyList(),
) = BenchmarkComparison(
    benchmark = benchmark,
    baselineIterations = 2,
    candidateIterations = 3,
    slower = slower,
    faster = faster,
    operations = operations,
    counters = counters,
    baselineOnly = baselineOnly,
    candidateOnly = candidateOnly,
)

internal fun operationComparison(name: String = OPERATION, moved: Boolean = true) = OperationComparison(
    name = name,
    baselineMeanNanos = 1000.0,
    candidateMeanNanos = 1500.0,
    deltaNanos = 500.0,
    deltaPercent = 50.0,
    baselineStdevNanos = 100.0,
    candidateStdevNanos = 150.0,
    noiseNanos = 250.0,
    moved = moved,
    baselineIterations = 2,
    candidateIterations = 3,
)

internal fun counterComparison(name: String = COUNTER) = CounterComparison(
    name = name,
    baselineMeanTotal = 2000.0,
    candidateMeanTotal = 2500.0,
    delta = 500.0,
    deltaPercent = 25.0,
    baselineIterations = 2,
    candidateIterations = 3,
)
