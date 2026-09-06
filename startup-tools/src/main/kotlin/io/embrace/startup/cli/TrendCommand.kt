package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.analysis.TrendReport
import io.embrace.startup.core.json.StartupJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files

/** `trend` - the former `trend_report.py`: baselines, drift and regressions per series from the store. */
class TrendCommand : CliktCommand(name = "trend") {

    private val store by option("--store", help = "the longitudinal store (JSONL)").path().required()
    private val baselineRuns by option("--baseline-runs", help = "earliest N published runs that form the fixed baseline")
        .int().default(TrendReport.DEFAULT_BASELINE_RUNS)
    private val jsonPath by option("--json", help = "write per-series verdicts here").path()

    override fun help(context: Context): String =
        "Report baselines, drift and regressions per series (device x SDK version x recipe x conditions) from the " +
            "longitudinal store, plus a separate version-comparison table."

    override fun run() {
        if (!Files.exists(store)) {
            echo("no store at $store", err = true)
            throw ProgramResult(1)
        }
        val (records, notes) = TrendReport.readStore(store)
        val result = TrendReport.run(records, baselineRuns, notes)
        echo(result.text, trailingNewline = false)
        jsonPath?.let { path ->
            val tree = buildJsonObject {
                result.json.forEach { (key, s) ->
                    put(
                        key,
                        JsonObject(
                            mapOf(
                                "baseline_median" to JsonPrimitive(s.baselineMedian),
                                "band_pct" to JsonPrimitive(s.bandPct),
                                "latest_delta_pct" to JsonPrimitive(s.latestDeltaPct),
                                "runs" to JsonPrimitive(s.runs),
                                "next_action" to JsonPrimitive(s.nextAction),
                            ),
                        ),
                    )
                }
            }
            Files.writeString(path, StartupJson.encodeToString(JsonObject.serializer(), tree))
            echo("wrote $path")
        }
    }
}
