package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.perfetto.Prebuilt
import io.embrace.analysis.perfetto.TraceProcessor
import io.embrace.analysis.reports.MatrixReport
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files

/** `matrix-report`: cross-cell comparison for a version x factor run. */
class MatrixReportCommand : CliktCommand(name = "matrix-report") {

    private val runDir by argument("run-dir", help = "directory holding the cells' cell-state.json subdirectories")
        .path(mustExist = true, canBeFile = false)
    private val slice by option("--slice", help = "window slice: emb-sdk-start (9.2.0+) or `composed` for older cells")
        .default(MatrixReport.DEFAULT_SLICE)
    private val jsonPath by option("--json").path()
    private val traceProcessor by option("--trace-processor", help = "path to a native trace_processor_shell").path(mustExist = true)

    override fun help(context: Context): String =
        "Per cell: n, window median/p90/max and per-pass medians; then the version table (reference cells) and the " +
            "factor table (each level vs the same version's reference cell)."

    override fun run() {
        val tp = TraceProcessor(Prebuilt.resolve(explicit = traceProcessor))
        val (cells, notes) = MatrixReport.collect(runDir, MatrixReport.windowReader(tp, slice), slice)
        echo(MatrixReport.report(cells, slice, notes), trailingNewline = false)
        jsonPath?.let {
            Files.writeString(it, StartupJson.encodeToString(JsonObject.serializer(), MatrixReport.toJson(cells)))
            echo("wrote $it")
        }
    }
}
