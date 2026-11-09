package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.iterations.IterationTrace
import io.embrace.android.embracesdk.internal.perfetto.iterations.discoverIterations
import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.aggregateIterations
import io.embrace.android.embracesdk.internal.perfetto.trace.TraceFormat
import io.embrace.android.embracesdk.internal.perfetto.trace.parseTrace
import io.embrace.android.embracesdk.internal.perfetto.trace.validateTrace
import okio.FileSystem
import okio.Path
import java.io.IOException
import kotlin.system.exitProcess

/**
 * The traces of the run collected into [dir], or exits saying why there are none to read.
 */
internal fun discoverRun(fileSystem: FileSystem, dir: Path): List<IterationTrace> {
    if (fileSystem.resolvedMetadata(dir)?.isDirectory != true) {
        System.err.println("no run directory at ${absolute(dir)}")
        exitProcess(EXIT_BAD_TRACE)
    }
    val iterations = try {
        discoverIterations(fileSystem, dir)
    } catch (exc: IOException) {
        System.err.println(exc.message)
        exitProcess(EXIT_BAD_TRACE)
    }
    if (iterations.isEmpty()) {
        System.err.println("no iteration traces in ${absolute(dir)}")
        exitProcess(EXIT_BAD_TRACE)
    }
    return iterations
}

/**
 * Reduces the [iterations] discovered in [dir] to the one aggregate that stands for the run, reporting
 * each trace as it is read.
 *
 * [operations] narrows the sections measured, as `--operations` names them; empty measures every one.
 */
internal fun loadRun(
    fileSystem: FileSystem,
    dir: Path,
    iterations: List<IterationTrace>,
    operations: List<String>,
): IterationsReport {
    val reports = iterations.groupBy(IterationTrace::benchmark)
        .mapValues { (_, traces) -> traces.map { loadIteration(fileSystem, it, operations) } }
    return aggregateIterations(dir.toString(), reports)
}

/** One line per benchmark of [iterations], naming it and how many iterations of it the run holds. */
internal fun describeBenchmarks(iterations: List<IterationTrace>, indent: String): String = buildString {
    iterations.groupBy(IterationTrace::benchmark).forEach { (benchmark, traces) ->
        val plural = if (traces.size == 1) "iteration" else "iterations"
        appendLine("${indent}benchmark: $benchmark (${traces.size} $plural)")
    }
}

private fun loadIteration(fileSystem: FileSystem, iteration: IterationTrace, operations: List<String>): StatsReport {
    val format = validateTrace(fileSystem, iteration.file)
    if (format != TraceFormat.PERFETTO) {
        System.err.println("${absolute(iteration.file)} is ${format.label}")
        exitProcess(EXIT_BAD_TRACE)
    }
    val trace = try {
        parseTrace(fileSystem, iteration.file)
    } catch (exc: IOException) {
        System.err.println(exc.message)
        exitProcess(EXIT_BAD_TRACE)
    }
    println("${describeIteration(fileSystem, iteration)}\n${summarise(trace)}")
    return statsReport(fileSystem, iteration.file, trace, operations)
}
