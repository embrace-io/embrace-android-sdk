package io.embrace.analysis.reports

import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.fixtures.Fixtures
import io.embrace.analysis.fixtures.Goldens
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
 * record). Stdout compared line for line minus the trailing `wrote ...` line; JSON as fields.
 */
class ReproducibilityReportTest {

    private val goldens: Path = Fixtures.root().toPath().resolve("goldens")

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
}
