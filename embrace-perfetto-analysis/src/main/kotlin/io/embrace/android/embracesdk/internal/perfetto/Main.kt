package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.proto.Trace
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

internal const val EXIT_USAGE = 1
internal const val EXIT_BAD_TRACE = 2

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
