package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.cli.CliOptions
import io.embrace.android.embracesdk.internal.perfetto.cli.CliSpec
import io.embrace.android.embracesdk.internal.perfetto.cli.asksForHelp
import io.embrace.android.embracesdk.internal.perfetto.cli.parseArgs
import io.embrace.android.embracesdk.internal.perfetto.iterations.IterationTrace
import io.embrace.android.embracesdk.internal.perfetto.iterations.discoverIterations
import io.embrace.android.embracesdk.internal.perfetto.report.renderIterations
import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.aggregateIterations
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

Every section is measured once per iteration, as that iteration's total, so a run of ten iterations
is ten observations of each. embrace-perfetto-analysis/README.md describes what the report holds.
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
    val reports = iterations.groupBy(IterationTrace::benchmark)
        .mapValues { (_, traces) -> traces.map { loadIteration(options, it) } }
    try {
        println(writeIterations(options, aggregateIterations(options.input.path, reports)))
    } catch (exc: IOException) {
        System.err.println("could not write the report: ${exc.message}")
        exitProcess(EXIT_BAD_OUTPUT)
    }
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

internal fun writeIterations(options: CliOptions, report: IterationsReport): String {
    options.output.writeText(renderIterations(options.format, report))
    return "wrote ${options.format.flag} statistics to ${options.output.path}"
}

/**
 */
private fun loadIteration(options: CliOptions, iteration: IterationTrace): StatsReport {
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
    println("${describeIteration(iteration)}\n${summarise(trace)}")
    return statsReport(iteration.file, trace, options.operations)
}
