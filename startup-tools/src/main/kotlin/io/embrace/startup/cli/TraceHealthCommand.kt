package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.perfetto.Prebuilt
import io.embrace.startup.perfetto.TraceHealth
import io.embrace.startup.perfetto.TraceProcessor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/**
 * `trace-health` - the former `trace_health.py` CLI: per-trace loss/canary verdicts over a directory
 * (recursively, `*.perfetto-trace` then `*.pftrace`) and the run-level summary with prevention advice.
 */
class TraceHealthCommand : CliktCommand(name = "trace-health") {

    private val tracesDir by argument("traces-dir").path(mustExist = true, canBeFile = false)
    private val canary by option("--canary", help = "slice that must exist in every good trace").default(TraceHealth.DEFAULT_CANARY)
    private val traceProcessor by option("--trace-processor", help = "path to a native trace_processor_shell").path(mustExist = true)
    private val showMeta by option("--show-meta", help = "also print metadata-only counters (off by default: they are noise)").flag()

    override fun help(context: Context): String =
        "Detect trace data loss BEFORE trusting any number derived from a trace: buffer loss, event-parse loss and a " +
            "canary slice, bucketed by what a non-zero counter can invalidate."

    override fun run() {
        val traces = listTraces(tracesDir)
        if (traces.isEmpty()) {
            echo("no traces under $tracesDir", err = true)
            throw ProgramResult(1)
        }
        val tp = TraceProcessor(Prebuilt.resolve(explicit = traceProcessor))
        val reports = traces.map { trace ->
            val report = TraceHealth.check(tp, trace, canary)
            if (report.verdict != TraceHealth.Verdict.OK || report.classLoadBurst) {
                echo(perTraceLine(report, trace, showMeta))
            }
            report
        }
        echo(TraceHealth.summarize(reports))
    }

    companion object {
        /** The Python's ordering: all `*.perfetto-trace` sorted, then all `*.pftrace` sorted. */
        fun listTraces(dir: Path): List<Path> {
            fun withSuffix(suffix: String) = Files.walk(dir).use { s ->
                s.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(suffix) }.toList()
            }.sorted()
            return withSuffix(".perfetto-trace") + withSuffix(".pftrace")
        }

        /** `  [<verdict>          ] <file>  canary=<n> k=v, k=v` - buffer and parse counters, meta only on request. */
        fun perTraceLine(report: TraceHealth.Report, trace: Path, showMeta: Boolean): String {
            val shown = LinkedHashMap<String, Long>()
            shown.putAll(report.buckets.getValue(TraceHealth.Bucket.BUFFER))
            shown.putAll(report.buckets.getValue(TraceHealth.Bucket.PARSE))
            if (showMeta) shown.putAll(report.buckets.getValue(TraceHealth.Bucket.META))
            val detail = shown.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}=${it.value}" }.ifEmpty { "none" }
            val burst = if (report.classLoadBurst) {
                "  first-session-classloads=${report.classLoadsInFirstSession}"
            } else {
                ""
            }
            return "  [${report.verdict.key.padEnd(VERDICT_W)}] ${trace.fileName}  canary=${report.canarySlices} $detail$burst"
        }

        private const val VERDICT_W = 17
    }
}
