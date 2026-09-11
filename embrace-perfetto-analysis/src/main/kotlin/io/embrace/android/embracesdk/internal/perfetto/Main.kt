package io.embrace.android.embracesdk.internal.perfetto

import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.system.exitProcess

internal const val EXIT_USAGE = 1
internal const val EXIT_BAD_TRACE = 2

/** The prefix every section the SDK emits carries, added by EmbTrace. */
internal const val EMB_PREFIX = "emb-"

/** How many sections the summary names before it stops. */
private const val TOP_SECTIONS = 5

private const val NANOS_PER_MILLI = 1_000_000.0

private const val USAGE = """
usage: analyseTrace <trace.perfetto.gz> [options]

  --dry-run   validate the inputs and report what would be analysed, then stop
  --help, -h  print this message

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
    println(describe(options, format))
    if (options.dryRun) {
        return
    }
    val trace = try {
        parseTrace(options.trace)
    } catch (exc: IOException) {
        System.err.println(exc.message)
        exitProcess(EXIT_BAD_TRACE)
    }
    println(summarise(trace))
    if (trace.hasAnomalies) {
        System.err.println(warning(trace))
    }
}

internal data class Options(val trace: File, val dryRun: Boolean)

/**
 * Returns null when the arguments do not name exactly one trace, which the caller reports as a
 * usage error.
 */
internal fun parseArgs(args: Array<String>): Options? {
    var trace: String? = null
    var dryRun = false
    args.forEach { arg ->
        when {
            arg == "--dry-run" -> dryRun = true
            arg.startsWith("-") -> return null
            trace != null -> return null
            else -> trace = arg
        }
    }
    return trace?.let { Options(File(it), dryRun) }
}

/** Reports the inputs the analysis runs against. */
internal fun describe(options: Options, format: TraceFormat): String = buildString {
    appendLine("perfetto trace analysis")
    appendLine("  trace: ${options.trace.path} (${options.trace.length()} bytes)")
    appendLine("  format: ${format.label}")
}

/** Reports what was read, and the sections that took the longest in total. */
internal fun summarise(trace: PerfettoTrace): String = buildString {
    val sections = trace.withPrefix(EMB_PREFIX)
    appendLine("  slices: ${trace.slices.size} across ${trace.threadIds.size} threads")
    appendLine("  span: ${millis(trace.durationNanos ?: 0)} ms")
    append("  $EMB_PREFIX sections: ${sections.size}")
    sections.groupBy(TraceSlice::name)
        .map { (name, slices) -> Section(name, slices.size, slices.sumOf(TraceSlice::durationNanos)) }
        .sortedByDescending(Section::totalNanos)
        .take(TOP_SECTIONS)
        .forEach { section ->
            appendLine()
            append("    ${section.name}: ${millis(section.totalNanos)} ms over ${calls(section.count)}")
        }
}

/** Names the events that could not be turned into slices, so a truncated trace is not read as a clean one. */
internal fun warning(trace: PerfettoTrace): String = "warning: " + listOf(
    "${trace.unmatchedEndCount} unmatched end events",
    "${trace.unclosedBeginCount} slices left open",
    "${trace.ignoredEventCount} events ignored",
).joinToString()

private fun calls(count: Int): String = if (count == 1) "1 call" else "$count calls"

private fun millis(nanos: Long): String = String.format(Locale.US, "%.2f", nanos / NANOS_PER_MILLI)
