package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.common.proc.Parallel
import io.embrace.analysis.perfetto.Prebuilt
import io.embrace.analysis.perfetto.TraceHealth
import io.embrace.analysis.perfetto.TraceProcessor
import io.embrace.analysis.records.store.StartupHealth

/**
 * `trace-health`: per-trace loss/canary verdicts over a directory (recursively, `*.perfetto-trace`
 * then `*.pftrace`) and the run-level summary with prevention advice.
 */
class TraceHealthCommand : CliktCommand(name = "trace-health") {

    private val tracesDir by argument("traces-dir").path(mustExist = true, canBeFile = false)
    private val canary by option("--canary", help = "slice that must exist in every good trace").default(StartupHealth.CANARY)
    private val traceProcessor by option("--trace-processor", help = "path to a native trace_processor_shell").path(mustExist = true)
    private val showMeta by option("--show-meta", help = "also print metadata-only counters (off by default: they are noise)").flag()

    override fun help(context: Context): String =
        "Detect trace data loss BEFORE trusting any number derived from a trace: buffer loss, event-parse loss and a " +
            "canary slice, bucketed by what a non-zero counter can invalidate."

    override fun run() {
        val traces = TraceHealth.listTraces(tracesDir)
        if (traces.isEmpty()) {
            echo("no traces under $tracesDir", err = true)
            throw ProgramResult(1)
        }
        val tp = TraceProcessor(Prebuilt.resolve(explicit = traceProcessor))
        // Checked in parallel, reported in input order: interleaved lines from several workers would be
        // unreadable, and the per-trace order is what a reader matches against the run directory.
        val profile = StartupHealth.profileFor(canary)
        val reports = Parallel.map(traces) { TraceHealth.check(tp, it, profile) }
        reports.forEachIndexed { index, report ->
            if (report.verdict != TraceHealth.Verdict.OK || report.classLoadBurst) {
                echo(TraceHealth.perTraceLine(report, traces[index], showMeta))
            }
        }
        echo(TraceHealth.summarize(reports, profile))
    }
}
