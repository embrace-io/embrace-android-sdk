package io.embrace.android.embracesdk.internal.perfetto

private const val UNNAMED_THREAD = "-"
private const val EMPTY_SECTION = "_none_"

private val FIXED_COLUMNS = listOf("operation", "thread", "tid", "count", "total", "wall%", "mean", "stdev", "min")

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
    appendLine("## Missing")
    appendLine()
    appendMissing(report.stats.missing)
}.trimEnd()

private fun StringBuilder.appendOperations(operations: List<OperationStats>) {
    val first = operations.firstOrNull()
    if (first == null) {
        appendLine(EMPTY_SECTION)
        return
    }
    val columns = FIXED_COLUMNS + first.percentiles.map { "p${it.rank}" } + "max"
    appendLine(row(columns))
    appendLine(row(columns.map { "---" }))
    operations.forEach { appendLine(row(cells(it))) }
}

private fun StringBuilder.appendMissing(missing: List<String>) {
    if (missing.isEmpty()) {
        appendLine(EMPTY_SECTION)
        return
    }
    missing.forEach { appendLine("- $it") }
}

private fun cells(stats: OperationStats): List<String> = listOf(
    stats.name,
    stats.threadName ?: UNNAMED_THREAD,
    stats.tid.toString(),
    stats.count.toString(),
    micros(stats.sumNanos),
    percent(stats.traceWindowPercent),
    micros(stats.meanNanos),
    micros(stats.stdevNanos),
    micros(stats.minNanos),
) + stats.percentiles.map { micros(it.durationNanos) } + micros(stats.maxNanos)

private fun row(cells: List<String>): String =
    cells.joinToString(" | ", "| ", " |") { it.replace("|", "\\|") }
