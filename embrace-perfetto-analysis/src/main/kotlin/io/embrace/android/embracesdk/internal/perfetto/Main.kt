package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.proto.Trace
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

internal const val EXIT_USAGE = 1
internal const val EXIT_BAD_TRACE = 2

private const val USAGE = """
usage: analyseTrace <trace.perfetto.gz> [options]

  --operations <a,b,c>      report statistics for these sections
  --all-operations          report statistics for every section the trace recorded
  --format markdown|json    how to render those statistics (default: markdown)
  --dry-run                 validate the inputs and report what would be analysed, then stop
  --help, -h                print this message

Normally run via scripts/analyse-trace.sh.
"""

fun main(args: Array<String>) {
    if (args.any { it == "--help" || it == "-h" }) {
        println(USAGE.trim())
        return
    }
    val options = parseArgs(args)
    if (options == null) {
        System.err.println(USAGE.trim())
        exitProcess(EXIT_USAGE)
    }
    if (!options.trace.isFile) {
        System.err.println("no trace file at ${options.trace.absolutePath}")
        exitProcess(EXIT_BAD_TRACE)
    }
    val format = validateTrace(options.trace)
    if (format != TraceFormat.PERFETTO) {
        System.err.println("${options.trace.absolutePath} is ${format.label}")
        exitProcess(EXIT_BAD_TRACE)
    }
    if (options.dryRun || !options.reportsStats) {
        println(describe(options, format))
    }
    if (options.dryRun) {
        return
    }
    val trace = try {
        parseTrace(options.trace)
    } catch (exc: IOException) {
        System.err.println(exc.message)
        exitProcess(EXIT_BAD_TRACE)
    }
    when {
        options.reportsStats -> println(render(options.format, statsReport(options, trace)))
        else -> println(summarise(trace))
    }
}

internal data class Options(
    val trace: File,
    val dryRun: Boolean = false,
    val operations: List<String> = emptyList(),
    val allOperations: Boolean = false,
    val format: ReportFormat = ReportFormat.MARKDOWN,
) {

    val reportsStats: Boolean get() = allOperations || operations.isNotEmpty()
}

/** Returns null for anything the caller should report as a usage error. */
internal fun parseArgs(args: Array<String>): Options? {
    var trace: String? = null
    var dryRun = false
    var operations: List<String>? = null
    var allOperations = false
    var format = ReportFormat.MARKDOWN
    var index = 0

    while (index < args.size) {
        val arg = args[index]
        when {
            arg == "--dry-run" -> dryRun = true
            arg == "--all-operations" -> allOperations = true
            arg == "--operations" -> operations = sectionNames(args.getOrNull(++index)) ?: return null
            arg == "--format" -> format = ReportFormat.from(args.getOrNull(++index).orEmpty()) ?: return null
            arg.startsWith("-") -> return null
            trace != null -> return null
            else -> trace = arg
        }
        index++
    }
    if (allOperations && operations != null) {
        return null
    }
    return trace?.let { Options(File(it), dryRun, operations.orEmpty(), allOperations, format) }
}

private fun sectionNames(value: String?): List<String>? {
    if (value == null || value.startsWith("-")) {
        return null
    }
    return value.split(",").takeIf { names -> names.none(String::isBlank) }
}

/** Reports the inputs the analysis runs against. */
internal fun describe(options: Options, format: TraceFormat): String = buildString {
    appendLine("perfetto trace analysis")
    appendLine("  trace: ${options.trace.path} (${options.trace.length()} bytes)")
    appendLine("  format: ${format.label}")
}

internal fun summarise(trace: Trace): String = buildString {
    val events = ftraceEvents(trace)
    val prints = printEvents(events)
    val model = TraceInterpreter().interpret(prints, threadNames(trace))
    appendLine("  packets: ${trace.packet.size}")
    appendLine("  ftrace events: ${events.size}")
    // ftrace calls this `pid`, but it holds a thread id
    appendLine("  atrace events: ${prints.size} across ${model.threads.size} threads")
    appendLine("  slices: ${model.sliceCount} of ${model.names.size} distinct sections")
    model.threads.values.forEach { timeline ->
        val named = timeline.name?.let { " ($it)" }.orEmpty()
        appendLine("    tid ${timeline.tid}$named: ${timeline.slices.size} slices")
    }
    append("  skipped: ${model.unclosed} unclosed, ${model.unopened} unopened, ${model.unsupported} unsupported")
}

internal fun statsReport(options: Options, trace: Trace): StatsReport {
    val events = ftraceEvents(trace)
    val model = TraceInterpreter().interpret(events, threadNames(trace))
    val window = traceWindowNanos(events)
    val requested = when {
        options.allOperations -> model.names.sorted()
        else -> options.operations
    }
    return StatsReport(
        tracePath = options.trace.path,
        traceSizeBytes = options.trace.length(),
        sliceCount = model.sliceCount,
        sectionCount = model.names.size,
        threadCount = model.threads.size,
        traceWindowNanos = window,
        stats = calculateStats(model, requested, window),
    )
}

internal fun render(format: ReportFormat, report: StatsReport): String = when (format) {
    ReportFormat.MARKDOWN -> renderMarkdown(report)
    ReportFormat.JSON -> renderJson(report)
}
