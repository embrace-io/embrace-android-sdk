package io.embrace.android.embracesdk.internal.perfetto

import kotlin.system.exitProcess

private val SPEC = CliSpec(
    command = "analyseIterations",
    inputs = listOf("<dir>"),
    notes = """
<dir> holds one .perfetto-trace per iteration, as scripts/macrobenchmark.sh collects them into
perf/macrobenchmark/<device>/. Normally run via scripts/analyse-trace-iterations.sh.

NOT IMPLEMENTED: this is a placeholder. Nothing parses these options or reads a trace yet, and
every invocation but --help exits $EXIT_NOT_IMPLEMENTED.
embrace-perfetto-analysis/README.md describes what it will produce.
""",
)

fun main(args: Array<String>) {
    if (asksForHelp(args)) {
        println(SPEC.usage)
        return
    }
    System.err.println("aggregating iterations is not implemented yet; run with --help for the intended usage")
    exitProcess(EXIT_NOT_IMPLEMENTED)
}
