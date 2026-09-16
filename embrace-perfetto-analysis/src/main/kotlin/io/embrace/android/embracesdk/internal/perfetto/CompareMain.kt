package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.cli.CliOptions
import io.embrace.android.embracesdk.internal.perfetto.cli.CliSpec
import io.embrace.android.embracesdk.internal.perfetto.cli.asksForHelp
import io.embrace.android.embracesdk.internal.perfetto.cli.parseArgs
import io.embrace.android.embracesdk.internal.perfetto.iterations.IterationTrace
import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport
import kotlin.system.exitProcess

private val SPEC = CliSpec(
    command = "compareIterations",
    inputs = listOf("<baseline-dir>", "<candidate-dir>"),
    notes = """
Both inputs are macrobenchmark run directories, the same thing analyseIterations takes one of, and each
is aggregated exactly as that command aggregates it: the aggregates themselves are internal, so nothing
has to be analysed first. --operations therefore narrows both runs alike. Normally run via
scripts/compare-trace-iterations.sh.

Both runs are read, but comparing them is NOT IMPLEMENTED: nothing diffs the two aggregates or writes a
report yet, so an invocation that gets that far exits $EXIT_NOT_IMPLEMENTED.
embrace-perfetto-analysis/README.md describes what it will produce.
""",
)

fun main(args: Array<String>) {
    if (asksForHelp(args)) {
        println(SPEC.usage)
        return
    }
    val options = parseArgs(SPEC, args)
    if (options == null) {
        System.err.println(SPEC.usage)
        exitProcess(EXIT_USAGE)
    }
    val (baselineDir, candidateDir) = options.inputs
    val baseline = discoverRun(baselineDir)
    val candidate = discoverRun(candidateDir)
    println(describeComparison(options, baseline, candidate))
    if (options.dryRun) {
        return
    }
    println(summariseRun(BASELINE, loadRun(baselineDir, baseline, options.operations)))
    println(summariseRun(CANDIDATE, loadRun(candidateDir, candidate, options.operations)))
    System.err.println("comparing two runs is not implemented yet; run with --help for the intended usage")
    exitProcess(EXIT_NOT_IMPLEMENTED)
}

internal fun describeComparison(
    options: CliOptions,
    baseline: List<IterationTrace>,
    candidate: List<IterationTrace>,
): String = buildString {
    val (baselineDir, candidateDir) = options.inputs
    appendLine("perfetto iteration comparison")
    appendLine("  $BASELINE: ${baselineDir.path}")
    append(describeBenchmarks(baseline, "    "))
    appendLine("  $CANDIDATE: ${candidateDir.path}")
    append(describeBenchmarks(candidate, "    "))
    append("  report: ${options.output.path} (${options.format.flag})")
}

internal fun summariseRun(role: String, report: IterationsReport): String =
    "  $role: aggregated ${tally(report.iterationCount, "iteration")} of ${tally(report.benchmarkCount, "benchmark")}"

private fun tally(count: Int, noun: String): String = "$count $noun${if (count == 1) "" else "s"}"

private const val BASELINE = "baseline"
private const val CANDIDATE = "candidate"
