package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.cli.CliOptions
import io.embrace.android.embracesdk.internal.perfetto.cli.CliSpec
import io.embrace.android.embracesdk.internal.perfetto.cli.asksForHelp
import io.embrace.android.embracesdk.internal.perfetto.cli.parseArgs
import io.embrace.android.embracesdk.internal.perfetto.iterations.IterationTrace
import io.embrace.android.embracesdk.internal.perfetto.report.renderIterations
import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport
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
    val iterations = discoverRun(options.input)
    println(describeIterations(options, iterations))
    if (options.dryRun) {
        return
    }
    val report = loadRun(options.input, iterations, options.operations)
    try {
        println(writeIterations(options, report))
    } catch (exc: IOException) {
        System.err.println("could not write the report: ${exc.message}")
        exitProcess(EXIT_BAD_OUTPUT)
    }
}

internal fun describeIterations(options: CliOptions, iterations: List<IterationTrace>): String = buildString {
    appendLine("perfetto iteration analysis")
    appendLine("  dir: ${options.input.path}")
    append(describeBenchmarks(iterations, "  "))
    append("  report: ${options.output.path} (${options.format.flag})")
}

internal fun describeIteration(iteration: IterationTrace): String =
    "${iteration.benchmark} iteration ${iteration.index}: ${iteration.file.name} (${iteration.file.length()} bytes)"

internal fun writeIterations(options: CliOptions, report: IterationsReport): String {
    options.output.writeText(renderIterations(options.format, report))
    return "wrote ${options.format.flag} statistics to ${options.output.path}"
}
