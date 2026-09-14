package io.embrace.analysis.campaign

import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.fixtures.Fixtures
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * `matrix-plan` against the skill's example plan (placeholders, 4x50 warning, two night breaks).
 * Stdout is compared line for line minus the trailing `wrote ...` line; the cell list as fields.
 */
class MatrixPlanTest {

    private val goldens: Path = Fixtures.root().toPath().resolve("goldens")

    @Test
    fun `matrix plan expands, orders and estimates the example plan exactly as the golden did`() {
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
    fun `a plan missing a required key or a device entry is refused with the golden message`() {
        val plan = StartupJson.parseToJsonElement(Files.readString(goldens.resolve("inputs/plan-example.json"))).jsonObject
        val missingKey = JsonObject(plan.filterKeys { it != "anchors" })
        val e1 = runCatching { MatrixPlan.report(missingKey) }.exceptionOrNull()
        assertEquals("plan is missing required key: anchors (see plan-example.json)", e1?.message)
        val noDevices = JsonObject(plan + ("devices" to JsonObject(emptyMap())))
        val e2 = runCatching { MatrixPlan.report(noDevices) }.exceptionOrNull()
        assertTrue(e2?.message?.startsWith("plan references device key(s) [") == true)
    }
}
