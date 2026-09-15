package io.embrace.android.embracesdk.internal.perfetto

import kotlin.system.exitProcess

private const val USAGE = """
usage: compareIterations <baseline.json> <candidate.json> [options]

  --format markdown|json|html  how to render the comparison (default: markdown)
  --output <file>              where to write it, required
  --dry-run                    report what would be compared, then stop
  --help, -h                   print this message

Both inputs are json aggregates written by analyseIterations, not trace directories and not
rendered reports. Normally run via scripts/compare-trace-iterations.sh.

NOT IMPLEMENTED: this is a placeholder. Nothing parses these options or reads an aggregate yet,
and every invocation but --help exits $EXIT_NOT_IMPLEMENTED.
embrace-perfetto-analysis/README.md describes what it will produce.
"""

fun main(args: Array<String>) {
    if (args.any { it == "--help" || it == "-h" }) {
        println(USAGE.trim())
        return
    }
    System.err.println("comparing aggregates is not implemented yet; run with --help for the intended usage")
    exitProcess(EXIT_NOT_IMPLEMENTED)
}
