package io.embrace.android.embracesdk.internal.perfetto

import java.io.File
import kotlin.system.exitProcess

internal const val EXIT_USAGE = 1
internal const val EXIT_BAD_TRACE = 2

private const val USAGE = """
usage: analyseTrace <trace.perfetto-trace> [options]

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
    println(describe(options))
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
internal fun describe(options: Options): String = buildString {
    appendLine("perfetto trace analysis")
    appendLine("  trace: ${options.trace.path} (${options.trace.length()} bytes)")
    if (!options.dryRun) {
        appendLine()
        appendLine("no analysis is implemented yet; only --dry-run is wired up.")
    }
}
