package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.AggregateCounterStats
import io.embrace.android.embracesdk.internal.perfetto.stats.AggregateOperationStats
import io.embrace.android.embracesdk.internal.perfetto.stats.BenchmarkStats
import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport

private val FIXED_COLUMNS = listOf("operation", "iters", "occ/iter", "wall%", "mean", "stdev", "cv%", "min")
private val COUNTER_COLUMNS = listOf("counter", "iters", "samples/iter", "mean", "min", "max", "sum")

internal fun renderIterationsMarkdown(report: IterationsReport): String = buildString {
    appendLine("# Perfetto iteration statistics")
    appendLine()
    appendLine("- run: ${report.runPath}")
    appendLine("- benchmarks: ${report.benchmarkCount}, iterations: ${report.iterationCount}")
    appendLine("- durations: microseconds")
    appendLine("- statistics: one observation per iteration, each an iteration's total for that section")
    appendLine()
    if (report.benchmarks.isEmpty()) {
        appendLine(EMPTY_SECTION)
    }
    report.benchmarks.forEach { appendBenchmark(it) }
}.trimEnd()

private fun StringBuilder.appendBenchmark(benchmark: BenchmarkStats) {
    appendLine("## ${benchmark.benchmark}")
    appendLine()
    appendLine("- iterations: ${benchmark.iterations.size}")
    appendLine("- trace window: mean ${micros(meanWindowNanos(benchmark))}, the span every wall% is a share of")
    appendLine()
    appendLine("### Operations")
    appendLine()
    appendOperations(benchmark.operations)
    appendLine()
    appendLine("### Counters")
    appendLine()
    appendLine("- totals: cumulative per iteration, summing every run a counter made within it")
    appendLine()
    appendTable(COUNTER_COLUMNS, benchmark.counters.map(::counterCells))
    appendLine()
    appendLine("### Partial")
    appendLine()
    appendBullets(benchmark.partial.map { "$it (recorded by some iterations, not all)" })
    appendLine()
    appendLine("### Missing")
    appendLine()
    appendBullets(benchmark.missing)
    appendLine()
}

private fun meanWindowNanos(benchmark: BenchmarkStats): Double = when {
    benchmark.iterations.isEmpty() -> 0.0
    else -> benchmark.iterations.sumOf { it.traceWindowNanos }.toDouble() / benchmark.iterations.size
}

private fun StringBuilder.appendOperations(operations: List<AggregateOperationStats>) {
    val ranks = operations.firstOrNull()?.percentiles.orEmpty().map { "p${it.rank}" }
    appendTable(FIXED_COLUMNS + ranks + "max", operations.map(::cells))
}

private fun cells(stats: AggregateOperationStats): List<String> = listOf(
    stats.name,
    stats.iterations.toString(),
    tally(stats.meanOccurrences),
    percent(stats.traceWindowPercent),
    micros(stats.meanNanos),
    micros(stats.stdevNanos),
    percent(stats.variationPercent),
    micros(stats.minNanos),
) + stats.percentiles.map { micros(it.durationNanos) } + micros(stats.maxNanos)

private fun counterCells(stats: AggregateCounterStats): List<String> = listOf(
    stats.name,
    stats.iterations.toString(),
    tally(stats.meanSamples),
    tally(stats.meanTotal),
    stats.minTotal.toString(),
    stats.maxTotal.toString(),
    stats.sumTotal.toString(),
)
