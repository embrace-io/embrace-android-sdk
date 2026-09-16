package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.AggregateCounterStats
import io.embrace.android.embracesdk.internal.perfetto.stats.AggregateOperationStats
import io.embrace.android.embracesdk.internal.perfetto.stats.BenchmarkStats
import io.embrace.android.embracesdk.internal.perfetto.stats.DEFAULT_PERCENTILES
import io.embrace.android.embracesdk.internal.perfetto.stats.IterationSummary
import io.embrace.android.embracesdk.internal.perfetto.stats.IterationValue
import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.Percentile

internal const val RUN = "perf/macrobenchmark/device"
internal const val SESSION = "SessionBenchmark.sessionEnd"
internal const val FIXTURE = "TraceFixtureBenchmark.sessionEndTraceFixture"
internal const val OPERATION = "emb-sdk-start"
internal const val COUNTER = "emb-sf-bytes-written"

internal fun iterationsReport(
    benchmarks: List<BenchmarkStats> = listOf(benchmarkStats()),
    iterationCount: Int = 2,
) = IterationsReport(
    runPath = RUN,
    benchmarkCount = benchmarks.size,
    iterationCount = iterationCount,
    benchmarks = benchmarks,
)

internal fun benchmarkStats(
    benchmark: String = SESSION,
    iterations: List<IterationSummary> = listOf(iterationSummary(0, 1_000_000), iterationSummary(1, 1_400_000)),
    operations: List<AggregateOperationStats> = listOf(aggregateOperation()),
    counters: List<AggregateCounterStats> = emptyList(),
    partial: List<String> = emptyList(),
    missing: List<String> = emptyList(),
) = BenchmarkStats(benchmark, iterations, operations, counters, partial, missing)

internal fun iterationSummary(index: Int, windowNanos: Long) = IterationSummary(
    index = index,
    tracePath = "iter00$index.perfetto-trace",
    traceSizeBytes = 2048,
    sliceCount = 12,
    sectionCount = 3,
    threadCount = 2,
    traceWindowNanos = windowNanos,
)

internal fun aggregateOperation(
    name: String = OPERATION,
    percentiles: List<Percentile> = DEFAULT_PERCENTILES.map { Percentile(it, 2000) },
    meanNanos: Double = 1500.0,
    stdevNanos: Double = 500.0,
) = AggregateOperationStats(
    name = name,
    iterations = 2,
    occurrences = 2,
    meanOccurrences = 1.0,
    traceWindowPercent = 0.25,
    minNanos = 1000,
    maxNanos = 2000,
    meanNanos = meanNanos,
    stdevNanos = stdevNanos,
    variationPercent = 33.3333,
    percentiles = percentiles,
    values = listOf(IterationValue(0, 1000), IterationValue(1, 2000)),
)

internal fun aggregateCounter(name: String = COUNTER, meanTotal: Double = 2560.0) = AggregateCounterStats(
    name = name,
    iterations = 2,
    sampleCount = 4,
    meanSamples = 2.0,
    minTotal = 1024,
    maxTotal = 4096,
    meanTotal = meanTotal,
    sumTotal = 5120,
    values = listOf(IterationValue(0, 1024), IterationValue(1, 4096)),
)
