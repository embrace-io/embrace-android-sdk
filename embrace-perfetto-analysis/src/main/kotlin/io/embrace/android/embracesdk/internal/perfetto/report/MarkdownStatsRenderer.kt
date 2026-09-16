package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.CounterStats
import io.embrace.android.embracesdk.internal.perfetto.stats.OperationStats
import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport

private val FIXED_COLUMNS = listOf("operation", "count", "total", "wall%", "mean", "stdev", "min")
private val COUNTER_COLUMNS = listOf("counter", "tid", "samples", "first", "last", "max", "total")

internal fun renderMarkdown(report: StatsReport): String = buildString {
    appendLine("# Perfetto trace statistics")
    appendLine()
    appendLine("- trace: ${report.tracePath} (${report.traceSizeBytes} bytes)")
    append("- recorded: ${report.sliceCount} slices of ${report.sectionCount} distinct sections")
    appendLine(" across ${report.threadCount} threads")
    appendLine("- durations: microseconds")
    appendLine("- trace window: ${micros(report.traceWindowNanos)}, the span every wall% is a share of")
    appendLine()
    appendLine("## Operations")
    appendLine()
    appendOperations(report.stats.operations)
    appendLine()
    appendLine("## Counters")
    appendLine()
    appendLine("- totals: cumulative, summing every run a counter made, so one that restarts still adds up")
    appendLine()
    appendTable(COUNTER_COLUMNS, report.stats.counters.map(::counterCells))
    appendLine()
    appendLine("## Missing")
    appendLine()
    appendBullets(report.stats.missing)
}.trimEnd()

private fun StringBuilder.appendOperations(operations: List<OperationStats>) {
    val ranks = operations.firstOrNull()?.percentiles.orEmpty().map { "p${it.rank}" }
    appendTable(FIXED_COLUMNS + ranks + "max", operations.map(::cells))
}

private fun cells(stats: OperationStats): List<String> = listOf(
    stats.name,
    stats.count.toString(),
    micros(stats.sumNanos),
    percent(stats.traceWindowPercent),
    micros(stats.meanNanos),
    micros(stats.stdevNanos),
    micros(stats.minNanos),
) + stats.percentiles.map { micros(it.durationNanos) } + micros(stats.maxNanos)

private fun counterCells(stats: CounterStats): List<String> = listOf(
    stats.name,
    stats.tids.joinToString(","),
    stats.sampleCount.toString(),
    stats.firstValue.toString(),
    stats.lastValue.toString(),
    stats.maxValue.toString(),
    stats.total.toString(),
)
