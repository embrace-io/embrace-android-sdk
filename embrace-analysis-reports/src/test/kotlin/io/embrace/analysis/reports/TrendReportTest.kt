package io.embrace.analysis.reports

import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.fixtures.Fixtures
import io.embrace.analysis.fixtures.Goldens
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * `trend` against the three frozen golden runs - the real store (every series has one
 * run, so the value is in the version-comparison block), the sweep store (three malformed records
 * grouped under `?`), and the synthetic store that exercises baseline, candidate, REGRESSION, the
 * TAIL-ONLY tag, ad-hoc runs, signal changes, pooled p99 and every next-action branch. Stdout is
 * compared line for line except the trailing `wrote <path>` line.
 */
class TrendReportTest {

    private val goldens: Path = Fixtures.root().toPath().resolve("goldens")
    private val longitudinal: Path = Fixtures.root().toPath().resolve("longitudinal")

    @Test
    fun `real store - twelve one-run series and the four-device version comparison`() {
        assertTrend(longitudinal.resolve("store.jsonl"), "trend_store")
    }

    @Test
    fun `sweep store - malformed records group under question marks and no comparison block appears`() {
        assertTrend(longitudinal.resolve("sweep-store.jsonl"), "trend_sweep_store")
    }

    @Test
    fun `synthetic store - every verdict, tag and next-action branch`() {
        val json = assertTrend(goldens.resolve("inputs/trend-store.jsonl"), "trend_synthetic")
        assertEquals(setOf("synth-a", "synth-b", "synth-c"), json.keys.map { it.substringBefore("|") }.toSet())
    }

    private fun assertTrend(store: Path, golden: String): Map<String, TrendReport.SeriesOut> {
        val (records, notes) = TrendReport.readStore(store)
        assertTrue(notes.isEmpty())
        val result = TrendReport.run(records, notes = notes)
        val want = Files.readString(goldens.resolve("$golden.stdout.txt")).lines()
            .dropLastWhile { it.isEmpty() }
            .dropLast(1) // wrote <absolute path>
        val got = result.text.lines().dropLastWhile { it.isEmpty() }
        assertEquals("$golden stdout", want.joinToString("\n"), got.joinToString("\n"))

        val wantJson = StartupJson.parseToJsonElement(Files.readString(goldens.resolve("$golden.json"))).jsonObject
        assertEquals("$golden json keys", wantJson.keys, result.json.keys)
        wantJson.forEach { (key, el) ->
            val w = el.jsonObject
            val g = result.json.getValue(key)
            assertEquals("$key baseline", w.getValue("baseline_median").jsonPrimitive.double, g.baselineMedian, 0.0)
            assertEquals("$key band", w.getValue("band_pct").jsonPrimitive.double, g.bandPct, 0.0)
            Goldens.assertClose("$key delta", w.getValue("latest_delta_pct").jsonPrimitive.double, g.latestDeltaPct)
            assertEquals("$key runs", w.getValue("runs").jsonPrimitive.int, g.runs)
            assertEquals("$key action", w.getValue("next_action").jsonPrimitive.content, g.nextAction)
        }
        return result.json
    }
}
