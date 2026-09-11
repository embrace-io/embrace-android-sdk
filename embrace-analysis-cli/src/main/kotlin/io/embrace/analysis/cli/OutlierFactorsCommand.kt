package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.perfetto.Prebuilt
import io.embrace.analysis.perfetto.TraceProcessor
import io.embrace.analysis.reports.OutlierFactors
import io.embrace.analysis.reports.StartupAnalysis
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Files

/**
 * `outlier-factors`: extracts the external-factor catalogue for every trace in a directory into
 * the `passN-factors.json` that `factors-report` and `hypothesis-tests` consume.
 */
class OutlierFactorsCommand : CliktCommand(name = "outlier-factors") {

    private val tracesDir by argument("traces-dir", help = "directory containing *.perfetto-trace files")
        .path(mustExist = true, canBeFile = false)
    private val outPath by argument("out-json", help = "where to write the factors dataset").path()
    private val traceProcessor by option("--trace-processor", help = "path to a native trace_processor_shell")
        .path(mustExist = true)

    override fun help(context: Context): String =
        "Per-trace catalogue of on-device factors inside the SDK-init window: delivered clock, cluster placement, " +
            "blocked thread states, ART work, GC, competing threads and processes, binder, swap."

    override fun run() {
        val tp = TraceProcessor(Prebuilt.resolve(explicit = traceProcessor))
        val traces = StartupAnalysis.listTraces(tracesDir)
        val data = traces.mapIndexed { i, trace ->
            val record = OutlierFactors.extract(tp, trace)
            echo("${i + 1}/${traces.size} ${trace.fileName}")
            record
        }
        Files.writeString(outPath, StartupJson.encodeToString(ListSerializer(OutlierFactors.Record.serializer()), data))
        echo("wrote $outPath")
    }
}
