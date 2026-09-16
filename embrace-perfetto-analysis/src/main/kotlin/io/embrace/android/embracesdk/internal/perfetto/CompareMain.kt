package io.embrace.android.embracesdk.internal.perfetto

import kotlin.system.exitProcess

private val SPEC = CliSpec(
    command = "compareIterations",
    inputs = listOf("<baseline.json>", "<candidate.json>"),
    selectsOperations = false,
    notes = """
Both inputs are json aggregates written by analyseIterations, not trace directories and not
rendered reports. Normally run via scripts/compare-trace-iterations.sh.

NOT IMPLEMENTED: this is a placeholder. Nothing parses these options or reads an aggregate yet,
and every invocation but --help exits $EXIT_NOT_IMPLEMENTED.
embrace-perfetto-analysis/README.md describes what it will produce.
""",
)

fun main(args: Array<String>) {
    if (asksForHelp(args)) {
        println(SPEC.usage)
        return
    }
    System.err.println("comparing aggregates is not implemented yet; run with --help for the intended usage")
    exitProcess(EXIT_NOT_IMPLEMENTED)
}
