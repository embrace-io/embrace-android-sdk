package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.cli.CliOptions
import io.embrace.android.embracesdk.internal.perfetto.cli.CliSpec
import io.embrace.android.embracesdk.internal.perfetto.cli.asksForHelp
import io.embrace.android.embracesdk.internal.perfetto.cli.parseArgs
import io.embrace.android.embracesdk.internal.perfetto.iterations.IterationTrace
import io.embrace.android.embracesdk.internal.perfetto.iterations.discoverIterations
import io.embrace.android.embracesdk.internal.perfetto.trace.TraceFormat
import io.embrace.android.embracesdk.internal.perfetto.trace.parseTrace
import io.embrace.android.embracesdk.internal.perfetto.trace.validateTrace
import java.io.IOException
import kotlin.system.exitProcess

private val SPEC = CliSpec(
    command = "analyseIterations",
    inputs = listOf("<dir>"),
    notes = """
<dir> holds one .perfetto-trace per iteration, as scripts/macrobenchmark.sh collects them into
perf/macrobenchmark/<device>/. The run's own benchmarkData.json decides which of the traces there
belong to it, so traces left behind by earlier runs are ignored. Without --output the report goes
beside the directory, as <dir>-report.<extension>. Normally run via scripts/analyse-trace-iterations.sh.

PARTLY IMPLEMENTED: the run's traces are found, read and summarised, and the aggregate report has a
model and its markdown, json and html renderers, but nothing folds the traces into one yet, so every
invocation but --help and --dry-run exits $EXIT_NOT_IMPLEMENTED.
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
    if (!options.input.isDirectory) {
        System.err.println("no run directory at ${options.input.absolutePath}")
        exitProcess(EXIT_BAD_TRACE)
    }
    val iterations = try {
        discoverIterations(options.input)
    } catch (exc: IOException) {
        System.err.println(exc.message)
        exitProcess(EXIT_BAD_TRACE)
    }
    if (iterations.isEmpty()) {
        System.err.println("no iteration traces in ${options.input.absolutePath}")
        exitProcess(EXIT_BAD_TRACE)
    }
    println(describeIterations(options, iterations))
    if (options.dryRun) {
        return
    }
    iterations.forEach { println(loadIteration(it)) }
    System.err.println("aggregating iterations is not implemented yet; no report was written to ${options.output.path}")
    exitProcess(EXIT_NOT_IMPLEMENTED)
}

internal fun describeIterations(options: CliOptions, iterations: List<IterationTrace>): String = buildString {
    appendLine("perfetto iteration analysis")
    appendLine("  dir: ${options.input.path}")
    iterations.groupBy(IterationTrace::benchmark).forEach { (benchmark, traces) ->
        val plural = if (traces.size == 1) "iteration" else "iterations"
        appendLine("  benchmark: $benchmark (${traces.size} $plural)")
    }
    append("  report: ${options.output.path} (${options.format.flag})")
}

internal fun describeIteration(iteration: IterationTrace): String =
    "${iteration.benchmark} iteration ${iteration.index}: ${iteration.file.name} (${iteration.file.length()} bytes)"

/**
 * Reads one iteration's trace and summarises what it holds.
 * A trace that cannot be read ends the run.
 */
private fun loadIteration(iteration: IterationTrace): String {
    val format = validateTrace(iteration.file)
    if (format != TraceFormat.PERFETTO) {
        System.err.println("${iteration.file.absolutePath} is ${format.label}")
        exitProcess(EXIT_BAD_TRACE)
    }
    val trace = try {
        parseTrace(iteration.file)
    } catch (exc: IOException) {
        System.err.println(exc.message)
        exitProcess(EXIT_BAD_TRACE)
    }
    return "${describeIteration(iteration)}\n${summarise(trace)}"
}
