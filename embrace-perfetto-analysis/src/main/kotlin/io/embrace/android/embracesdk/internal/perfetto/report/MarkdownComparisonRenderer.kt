package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.BenchmarkComparison
import io.embrace.android.embracesdk.internal.perfetto.stats.ComparisonReport
import io.embrace.android.embracesdk.internal.perfetto.stats.CounterComparison
import io.embrace.android.embracesdk.internal.perfetto.stats.OperationComparison

private val OPERATION_COLUMNS =
    listOf("operation", "base", "cand", "delta", "delta%", "noise", "moved", "base iters", "cand iters")
private val COUNTER_COLUMNS = listOf("counter", "base", "cand", "delta", "delta%", "base iters", "cand iters")
private const val MOVED = "yes"
private const val WITHIN_NOISE = "no"

internal fun renderComparisonMarkdown(report: ComparisonReport): String = buildString {
    appendLine("# Perfetto run comparison")
    appendLine()
    appendLine("- baseline: ${report.baselinePath}")
    appendLine("- candidate: ${report.candidatePath}")
    appendLine("- durations: microseconds")
    appendLine("- delta: the candidate less the baseline, so a positive delta is a regression")
    appendLine("- moved: whether the delta cleared the noise, which is both runs' deviations added together")
    appendLine()
    if (report.benchmarks.isEmpty()) {
        appendLine(EMPTY_SECTION)
        appendLine()
    }
    report.benchmarks.forEach { appendBenchmark(it) }
    appendLine("## Benchmarks only in baseline")
    appendLine()
    appendBullets(report.baselineOnly)
    appendLine()
    appendLine("## Benchmarks only in candidate")
    appendLine()
    appendBullets(report.candidateOnly)
}.trimEnd()

private fun StringBuilder.appendBenchmark(benchmark: BenchmarkComparison) {
    appendLine("## ${benchmark.benchmark}")
    appendLine()
    appendLine("- iterations: ${benchmark.baselineIterations} baseline, ${benchmark.candidateIterations} candidate")
    append("- moved: ${benchmark.slower} slower, ${benchmark.faster} faster")
    appendLine(" of ${benchmark.operations.size} sections compared")
    appendLine()
    appendLine("### Operations")
    appendLine()
    appendTable(OPERATION_COLUMNS, benchmark.operations.map(::cells))
    appendLine()
    appendLine("### Counters")
    appendLine()
    appendTable(COUNTER_COLUMNS, benchmark.counters.map(::counterCells))
    appendLine()
    appendLine("### Only in baseline")
    appendLine()
    appendBullets(benchmark.baselineOnly)
    appendLine()
    appendLine("### Only in candidate")
    appendLine()
    appendBullets(benchmark.candidateOnly)
    appendLine()
}

private fun cells(stats: OperationComparison): List<String> = listOf(
    stats.name,
    micros(stats.baselineMeanNanos),
    micros(stats.candidateMeanNanos),
    micros(stats.deltaNanos),
    percent(stats.deltaPercent),
    micros(stats.noiseNanos),
    if (stats.moved) MOVED else WITHIN_NOISE,
    stats.baselineIterations.toString(),
    stats.candidateIterations.toString(),
)

private fun counterCells(stats: CounterComparison): List<String> = listOf(
    stats.name,
    tally(stats.baselineMeanTotal),
    tally(stats.candidateMeanTotal),
    tally(stats.delta),
    percent(stats.deltaPercent),
    stats.baselineIterations.toString(),
    stats.candidateIterations.toString(),
)
