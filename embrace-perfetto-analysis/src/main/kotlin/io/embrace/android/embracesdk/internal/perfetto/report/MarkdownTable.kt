package io.embrace.android.embracesdk.internal.perfetto.report

internal const val EMPTY_SECTION = "_none_"

internal fun row(cells: List<String>): String =
    cells.joinToString(" | ", "| ", " |") { it.replace("|", "\\|") }

internal fun StringBuilder.appendTable(columns: List<String>, rows: List<List<String>>) {
    if (rows.isEmpty()) {
        appendLine(EMPTY_SECTION)
        return
    }
    appendLine(row(columns))
    appendLine(row(columns.map { "---" }))
    rows.forEach { appendLine(row(it)) }
}

internal fun StringBuilder.appendBullets(items: List<String>) {
    if (items.isEmpty()) {
        appendLine(EMPTY_SECTION)
        return
    }
    items.forEach { appendLine("- $it") }
}
