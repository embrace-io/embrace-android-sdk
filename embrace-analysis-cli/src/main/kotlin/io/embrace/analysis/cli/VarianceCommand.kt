package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.common.proc.Parallel
import io.embrace.analysis.perfetto.Prebuilt
import io.embrace.analysis.perfetto.TraceProcessor
import io.embrace.analysis.reports.StartupAnalysis
import io.embrace.analysis.reports.VarianceAnalysis
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Files

/**
 * `variance`: per-iteration variance over a directory of traces; `--json` writes the dataset that
 * `hypothesis-tests`, `factors-report` and `cross-device-sections` consume, `--out` tees the
 * report to a file.
 */
class VarianceCommand : CliktCommand(name = "variance") {

    private val tracesDir by argument("traces-dir", help = "directory containing *.perfetto-trace files")
        .path(mustExist = true, canBeFile = false)
    private val traceProcessor by option("--trace-processor", help = "path to a native trace_processor_shell")
        .path(mustExist = true)
    private val jsonPath by option("--json", help = "write the raw per-iteration dataset (the passN.json schema) here")
        .path()
    private val outPath by option("--out", help = "also write the printed report to this file").path()
    private val littleCpus by option(
        "--little-cpus",
        help = "comma-separated cpu ids of the little cluster (take it from `probe`; the default is wrong on any " +
            "device whose little cluster is not cpu0-3 and fails silently)",
    ).default("0,1,2,3")

    override fun help(context: Context): String =
        "Per-iteration variance analysis: iteration matrix, per-section fluctuation and correlation with the " +
            "window, outlier decomposition, thread-state split, per-CPU residency."

    override fun run() {
        val little = littleCpus.split(",").map { it.trim().toInt() }.toSet()
        val tp = TraceProcessor(Prebuilt.resolve(explicit = traceProcessor))
        val data = Parallel.map(StartupAnalysis.listTraces(tracesDir)) { VarianceAnalysis.extract(tp, it) }
        jsonPath?.let {
            Files.writeString(it, StartupJson.encodeToString(ListSerializer(VarianceAnalysis.Record.serializer()), data))
        }
        val text = VarianceAnalysis.report(data, little)
        echo(text, trailingNewline = false)
        outPath?.let { Files.writeString(it, text) }
    }
}
