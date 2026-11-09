package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.cli.CliOptions
import io.embrace.android.embracesdk.internal.perfetto.cli.CliSpec
import io.embrace.android.embracesdk.internal.perfetto.cli.asksForHelp
import io.embrace.android.embracesdk.internal.perfetto.cli.parseArgs
import io.embrace.android.embracesdk.internal.perfetto.iterations.IterationTrace
import io.embrace.android.embracesdk.internal.perfetto.report.renderComparison
import io.embrace.android.embracesdk.internal.perfetto.stats.ComparisonReport
import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.compareRuns
import okio.FileSystem
import java.io.IOException
import kotlin.system.exitProcess

private val SPEC = CliSpec(
    command = "compareIterations",
    inputs = listOf("<baseline-dir>", "<candidate-dir>"),
    notes = """
Both inputs are macrobenchmark run directories, the same thing analyseIterations takes one of, and each
is aggregated exactly as that command aggregates it: the aggregates themselves are internal, so nothing
has to be analysed first. --operations therefore narrows both runs alike. Normally run via
scripts/compare-trace-iterations.sh.

Both runs are read and compared, section by section. A section only counts as having moved when the
shift in its mean clears both runs' deviations added together, so a noisy run does not read as a
regression. Without --output the report goes beside the baseline, named for both runs, so it does not
write over what either aggregates to on its own.

embrace-perfetto-analysis/README.md describes what the report holds.
""",
)

fun main(args: Array<String>) = compareIterations(args, FileSystem.SYSTEM)

internal fun compareIterations(args: Array<String>, fileSystem: FileSystem) {
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
    val baseline = discoverRun(fileSystem, baselineDir)
    val candidate = discoverRun(fileSystem, candidateDir)
    println(describeComparison(options, baseline, candidate))
    if (options.dryRun) {
        return
    }
    val baselineRun = loadRun(fileSystem, baselineDir, baseline, options.operations)
    val candidateRun = loadRun(fileSystem, candidateDir, candidate, options.operations)
    println(summariseRun(BASELINE, baselineRun))
    println(summariseRun(CANDIDATE, candidateRun))
    val comparison = compareRuns(baselineRun, candidateRun)
    println(summariseComparison(comparison))
    try {
        println(writeComparison(fileSystem, options, comparison))
    } catch (exc: IOException) {
        System.err.println("could not write the report: ${exc.message}")
        exitProcess(EXIT_BAD_OUTPUT)
    }
}

internal fun describeComparison(
    options: CliOptions,
    baseline: List<IterationTrace>,
    candidate: List<IterationTrace>,
): String = buildString {
    val (baselineDir, candidateDir) = options.inputs
    appendLine("perfetto iteration comparison")
    appendLine("  $BASELINE: $baselineDir")
    append(describeBenchmarks(baseline, "    "))
    appendLine("  $CANDIDATE: $candidateDir")
    append(describeBenchmarks(candidate, "    "))
    append("  report: ${options.output} (${options.format.flag})")
}

internal fun summariseRun(role: String, report: IterationsReport): String =
    "  $role: aggregated ${tally(report.iterationCount, "iteration")} of ${tally(report.benchmarkCount, "benchmark")}"

internal fun summariseComparison(report: ComparisonReport): String = buildString {
    report.benchmarks.forEach { benchmark ->
        val counts = listOfNotNull(
            "${tally(benchmark.operations.size, "section")} compared",
            "${benchmark.slower} slower",
            "${benchmark.faster} faster",
            "${benchmark.baselineOnly.size} only in $BASELINE".takeIf { benchmark.baselineOnly.isNotEmpty() },
            "${benchmark.candidateOnly.size} only in $CANDIDATE".takeIf { benchmark.candidateOnly.isNotEmpty() },
        )
        appendLine("  ${benchmark.benchmark}: ${counts.joinToString(", ")}")
    }
    report.baselineOnly.forEach { appendLine("  $it: only in $BASELINE, so nothing to compare it with") }
    report.candidateOnly.forEach { appendLine("  $it: only in $CANDIDATE, so nothing to compare it with") }
    if (isEmpty()) {
        append("  neither run has a benchmark the other does, so nothing was compared")
    }
}.trimEnd()

internal fun writeComparison(fileSystem: FileSystem, options: CliOptions, report: ComparisonReport): String {
    val rendered = renderComparison(options.format, report)
    fileSystem.write(options.output) { writeUtf8(rendered) }
    return "wrote ${options.format.flag} comparison to ${options.output}"
}

private fun tally(count: Int, noun: String): String = "$count $noun${if (count == 1) "" else "s"}"

private const val BASELINE = "baseline"
private const val CANDIDATE = "candidate"
