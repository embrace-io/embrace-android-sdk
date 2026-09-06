package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.analysis.ReproducibilityReport
import io.embrace.startup.campaign.MatrixPlan
import io.embrace.startup.core.json.StartupJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files

/** `reproducibility` - the former `reproducibility_report.py`: per-cell contributor agreement in the corpus. */
class ReproducibilityCommand : CliktCommand(name = "reproducibility") {

    private val corpus by option("--corpus", help = "the shared corpus (JSONL of contributions)").path().required()
    private val jsonPath by option("--json", help = "write per-cell verdicts here").path()

    override fun help(context: Context): String =
        "Per-cell reproducibility: do independent contributors on the same model agree on median, tail and shape? " +
            "Tolerance comes from the submissions' own within-run spread; disagreeing cells rank provenance diffs."

    override fun run() {
        if (!Files.exists(corpus)) {
            echo("no corpus at $corpus", err = true)
            throw ProgramResult(1)
        }
        val (records, notes) = ReproducibilityReport.readCorpus(corpus)
        val result = ReproducibilityReport.run(records, notes)
        echo(result.text, trailingNewline = false)
        jsonPath?.let { path ->
            val tree = JsonObject(
                result.json.mapValues { (_, cell) ->
                    when (cell) {
                        is ReproducibilityReport.CellOut.Insufficient -> JsonObject(
                            mapOf("status" to JsonPrimitive("insufficient data"), "submissions" to JsonPrimitive(cell.submissions)),
                        )
                        is ReproducibilityReport.CellOut.Judged -> JsonObject(
                            mapOf(
                                "status" to JsonPrimitive(cell.status),
                                "median_spread_pct" to JsonPrimitive(cell.medianSpreadPct),
                                "p90_spread_pct" to JsonPrimitive(cell.p90SpreadPct),
                                "tolerance_pct" to JsonPrimitive(cell.tolerancePct),
                                "contributors" to JsonPrimitive(cell.contributors),
                                "units" to JsonPrimitive(cell.units),
                                "candidates" to JsonArray(cell.candidates.map { JsonPrimitive(it) }),
                            ),
                        )
                    }
                },
            )
            Files.writeString(path, StartupJson.encodeToString(JsonObject.serializer(), tree))
            echo("wrote $path")
        }
    }
}

/** `matrix-plan` - the former `matrix_plan.py`: expand a plan into ordered cells with a wall-clock estimate. */
class MatrixPlanCommand : CliktCommand(name = "matrix-plan") {

    private val plan by argument("plan", help = "the plan JSON (see the skill's plan-example.json)").path(mustExist = true)
    private val emit by option("--emit", help = "write the ordered cell list here").path()

    override fun help(context: Context): String =
        "Expand a version x factor matrix plan into an ordered, interleaved cell list with a wall-clock estimate and " +
            "the controls checklist. Dry-run: nothing touches a device."

    override fun run() {
        val planJson = StartupJson.parseToJsonElement(Files.readString(plan)).jsonObject
        val result = try {
            MatrixPlan.report(planJson)
        } catch (e: MatrixPlan.PlanError) {
            echo(e.message, err = true)
            throw ProgramResult(1)
        }
        echo(result.text, trailingNewline = false)
        emit?.let { path ->
            Files.writeString(path, StartupJson.encodeToString(JsonObject.serializer(), MatrixPlan.emit(planJson, result.cells)))
            echo("wrote $path - run cells with: startup-tools cell-runner --cells $path --cell <id>")
        }
    }
}
