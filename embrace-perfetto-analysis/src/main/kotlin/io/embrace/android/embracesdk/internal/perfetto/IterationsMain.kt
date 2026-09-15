package io.embrace.android.embracesdk.internal.perfetto

import kotlin.system.exitProcess

private const val USAGE = """
usage: analyseIterations <dir> [options]

  --operations <a,b,c>         report statistics for these sections
  --all-operations             report statistics for every section the traces recorded
  --format markdown|json|html  how to render those statistics (default: markdown)
  --output <file>              where to write them, required whenever they are asked for
  --dry-run                    report what would be aggregated, then stop
  --help, -h                   print this message

<dir> holds one .perfetto-trace per iteration, as scripts/macrobenchmark.sh collects them into
perf/macrobenchmark/<device>/. Normally run via scripts/analyse-trace-iterations.sh.

NOT IMPLEMENTED: this is a placeholder. Nothing parses these options or reads a trace yet, and
every invocation but --help exits $EXIT_NOT_IMPLEMENTED.
embrace-perfetto-analysis/README.md describes what it will produce.
"""

fun main(args: Array<String>) {
    if (args.any { it == "--help" || it == "-h" }) {
        println(USAGE.trim())
        return
    }
    System.err.println("aggregating iterations is not implemented yet; run with --help for the intended usage")
    exitProcess(EXIT_NOT_IMPLEMENTED)
}
