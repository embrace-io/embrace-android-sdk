package io.embrace.android.embracesdk.internal.perfetto

import java.io.File

private val VALUED = setOf("--operations", "--format", "--output")
private val FLAGS = setOf("--all-operations", "--dry-run")

/** Whether the arguments ask for the usage message rather than an analysis. */
internal fun asksForHelp(args: Array<String>): Boolean = args.any { it == "--help" || it == "-h" }

/**
 * Reads [args] against what [spec] accepts, filling in what was left out.
 *
 * Returns null for anything the caller should report as a usage error.
 */
internal fun parseArgs(spec: CliSpec, args: Array<String>): CliOptions? {
    val inputs = mutableListOf<String>()
    val values = mutableMapOf<String, String>()
    val flags = mutableSetOf<String>()
    var index = 0

    while (index < args.size) {
        val arg = args[index]
        when {
            arg in FLAGS -> flags += arg
            arg in VALUED -> values[arg] = optionValue(args.getOrNull(++index)) ?: return null
            arg.startsWith("-") -> return null
            else -> inputs += arg
        }
        index++
    }
    return build(spec, inputs, values, flags)
}

/**
 * Turns what the arguments said into the options an analysis runs under, or null if they do not name one
 * analysis: the wrong number of inputs, an option this command does not take, or sections named while
 * every section is also asked for.
 */
private fun build(spec: CliSpec, inputs: List<String>, values: Map<String, String>, flags: Set<String>): CliOptions? {
    val operations = values["--operations"]?.split(",") ?: emptyList()
    val allOperations = "--all-operations" in flags
    if (inputs.size != spec.inputs.size) return null
    if (!spec.selectsOperations && (operations.isNotEmpty() || allOperations)) return null
    if (allOperations && operations.isNotEmpty()) return null
    if (operations.any(String::isBlank)) return null

    val format = ReportFormat.from(values["--format"] ?: ReportFormat.MARKDOWN.flag) ?: return null
    val files = inputs.map(::File)
    return CliOptions(
        inputs = files,
        dryRun = "--dry-run" in flags,
        operations = operations,
        format = format,
        output = values["--output"]?.let(::File) ?: reportPath(files.first(), format),
    )
}

/**
 * Where a report goes when `--output` did not say: alongside the input it was read from, named after it.
 *
 * `perf/run/trace.perfetto.gz` reports to `perf/run/trace-report.md`. Everything from the first dot is an
 * extension to drop, since a trace arrives as `.perfetto.gz` or `.perfetto-trace`.
 */
private fun reportPath(input: File, format: ReportFormat): File {
    val base = input.name.substringBefore('.')
    val name = if (base.isBlank()) "report" else "$base-report"
    return File(input.parentFile, "$name.${format.extension}")
}

/** An option's value is whatever follows it, unless that is another option or nothing at all. */
private fun optionValue(value: String?): String? = value?.takeUnless { it.startsWith("-") }
