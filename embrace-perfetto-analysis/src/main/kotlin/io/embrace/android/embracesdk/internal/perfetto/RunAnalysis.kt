package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.iterations.IterationTrace
import io.embrace.android.embracesdk.internal.perfetto.iterations.discoverIterations
import io.embrace.android.embracesdk.internal.perfetto.stats.IterationsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.aggregateIterations
import io.embrace.android.embracesdk.internal.perfetto.trace.TraceFormat
import io.embrace.android.embracesdk.internal.perfetto.trace.parseTrace
import io.embrace.android.embracesdk.internal.perfetto.trace.validateTrace
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess


/** The traces of the run collected into [dir], or exits saying why there are none to read. */
internal fun discoverRun(dir: File): List<IterationTrace> {
    if (!dir.isDirectory) {
        System.err.println("no run directory at ${dir.absolutePath}")
        exitProcess(EXIT_BAD_TRACE)
    }
    val iterations = try {
        discoverIterations(dir)
    } catch (exc: IOException) {
        System.err.println(exc.message)
        exitProcess(EXIT_BAD_TRACE)
    }
    if (iterations.isEmpty()) {
        System.err.println("no iteration traces in ${dir.absolutePath}")
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
internal fun loadRun(dir: File, iterations: List<IterationTrace>, operations: List<String>): IterationsReport {
    val reports = iterations.groupBy(IterationTrace::benchmark)
        .mapValues { (_, traces) -> traces.map { loadIteration(it, operations) } }
    return aggregateIterations(dir.path, reports)
}

/** One line per benchmark of [iterations], naming it and how many iterations of it the run holds. */
internal fun describeBenchmarks(iterations: List<IterationTrace>, indent: String): String = buildString {
    iterations.groupBy(IterationTrace::benchmark).forEach { (benchmark, traces) ->
        val plural = if (traces.size == 1) "iteration" else "iterations"
        appendLine("${indent}benchmark: $benchmark (${traces.size} $plural)")
    }
}

private fun loadIteration(iteration: IterationTrace, operations: List<String>): StatsReport {
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
    return statsReport(iteration.file, trace, operations)
}
