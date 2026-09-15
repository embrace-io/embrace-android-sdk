package io.embrace.android.embracesdk.internal.perfetto

internal data class CliSpec(
    val command: String,
    val inputs: List<String>,
    val selectsOperations: Boolean = true,
    val notes: String = "",
) {

    val usage: String get() = buildString {
        appendLine("usage: $command ${inputs.joinToString(" ")} [options]")
        appendLine()
        if (selectsOperations) {
            appendLine("  --operations <a,b,c>         report statistics for these sections (default: all of them)")
            appendLine("  --all-operations             report statistics for every section recorded, as is the default")
        }
        appendLine("  --format markdown|json|html  how to render the report (default: markdown)")
        appendLine("  --output <file>              where to write it (default: alongside ${inputs.first()})")
        appendLine("  --dry-run                    report what would be analysed, then stop")
        appendLine("  --help, -h                   print this message")
        if (notes.isNotBlank()) {
            appendLine()
            appendLine(notes.trim())
        }
    }.trim()
}
