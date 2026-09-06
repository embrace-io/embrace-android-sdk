package io.embrace.startup.analysis

import io.embrace.startup.Goldens
import io.embrace.startup.campaign.MatrixPlan
import io.embrace.startup.core.json.SchemaRoundTripTest
import io.embrace.startup.core.json.StartupJson
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * `reproducibility` against the hand-built six-record corpus (a reproduced flagship cell, a
 * disagreeing mid-tier cell with three provenance diffs, a single-contributor cell, an ignored n=0
 * record) and `matrix-plan` against the skill's example plan (placeholders, 4x50 warning, two night
 * breaks). Stdout compared line for line minus the trailing `wrote ...` line; JSON as fields.
 */
class ReproducibilityAndPlanTest {

    private val goldens: Path = SchemaRoundTripTest.fixturesRoot().toPath().resolve("goldens")

    @Test
    fun `reproducibility report and verdict JSON reproduce on the synthetic corpus`() {
        val (records, notes) = ReproducibilityReport.readCorpus(goldens.resolve("inputs/corpus.jsonl"))
        assertTrue(notes.isEmpty())
        assertEquals(6, records.size)
        val result = ReproducibilityReport.run(records)
        val want = Files.readString(goldens.resolve("reproducibility.stdout.txt")).lines().dropLastWhile {
            it.isEmpty()
        }.dropLast(1)
        assertEquals(want.joinToString("\n"), result.text.lines().dropLastWhile { it.isEmpty() }.joinToString("\n"))

        val wantJson = StartupJson.parseToJsonElement(Files.readString(goldens.resolve("reproducibility.json"))).jsonObject
        assertEquals(wantJson.keys, result.json.keys)
        wantJson.forEach { (key, el) ->
            val w = el.jsonObject
            when (val g = result.json.getValue(key)) {
                is ReproducibilityReport.CellOut.Insufficient -> {
                    assertEquals("$key status", "insufficient data", w.getValue("status").jsonPrimitive.content)
                    assertEquals("$key submissions", w.getValue("submissions").jsonPrimitive.int, g.submissions)
                }
                is ReproducibilityReport.CellOut.Judged -> {
                    assertEquals("$key status", w.getValue("status").jsonPrimitive.content, g.status)
                    Goldens.assertClose("$key median spread", w.getValue("median_spread_pct").jsonPrimitive.double, g.medianSpreadPct)
                    Goldens.assertClose("$key p90 spread", w.getValue("p90_spread_pct").jsonPrimitive.double, g.p90SpreadPct)
                    assertEquals("$key tol", w.getValue("tolerance_pct").jsonPrimitive.double, g.tolerancePct, 0.0)
                    assertEquals("$key contributors", w.getValue("contributors").jsonPrimitive.int, g.contributors)
                    assertEquals("$key units", w.getValue("units").jsonPrimitive.int, g.units)
                    assertEquals("$key candidates", w.getValue("candidates").jsonArray.map { it.jsonPrimitive.content }, g.candidates)
                }
            }
        }
    }

    @Test
    fun `matrix plan expands, orders and estimates the example plan exactly as the Python did`() {
        val plan = StartupJson.parseToJsonElement(Files.readString(goldens.resolve("inputs/plan-example.json"))).jsonObject
        val result = MatrixPlan.report(plan)
        // The golden ends "<checklist>\n\nwrote <path>": drop the wrote line, then the blank the checklist leaves.
        val want = Files.readString(goldens.resolve("matrix_plan.stdout.txt")).lines().dropLastWhile {
            it.isEmpty()
        }.dropLast(1).dropLastWhile { it.isEmpty() }
        assertEquals(want.joinToString("\n"), result.text.lines().dropLastWhile { it.isEmpty() }.joinToString("\n"))

        val wantJson = StartupJson.parseToJsonElement(Files.readString(goldens.resolve("matrix_plan.json"))).jsonObject
        assertEquals(wantJson.getValue("plan"), plan)
        val wantCells = wantJson.getValue("cells").jsonArray.map { it.jsonObject }
        assertEquals(wantCells.size, result.cells.size)
        wantCells.zip(result.cells).forEach { (w, g) ->
            assertEquals(w.getValue("id").jsonPrimitive.content, g.id)
            assertEquals(w.getValue("version").jsonPrimitive.content, g.version)
            assertEquals(w.getValue("device").jsonPrimitive.content, g.device)
            assertEquals(w.getValue("group").jsonPrimitive.content, g.group)
            assertEquals(w.getValue("levels").jsonObject, g.toJson().getValue("levels").jsonObject)
        }
    }

    @Test
    fun `a plan missing a required key or a device entry is refused with the Python message`() {
        val plan = StartupJson.parseToJsonElement(Files.readString(goldens.resolve("inputs/plan-example.json"))).jsonObject
        val missingKey = kotlinx.serialization.json.JsonObject(plan.filterKeys { it != "anchors" })
        val e1 = runCatching { MatrixPlan.report(missingKey) }.exceptionOrNull()
        assertEquals("plan is missing required key: anchors (see plan-example.json)", e1?.message)
        val noDevices = kotlinx.serialization.json.JsonObject(plan + ("devices" to kotlinx.serialization.json.JsonObject(emptyMap())))
        val e2 = runCatching { MatrixPlan.report(noDevices) }.exceptionOrNull()
        assertTrue(e2?.message?.startsWith("plan references device key(s) [") == true)
    }
}
