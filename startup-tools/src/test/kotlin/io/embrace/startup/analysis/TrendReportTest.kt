package io.embrace.startup.analysis

import io.embrace.startup.Goldens
import io.embrace.startup.cli.TraceHealthCommand
import io.embrace.startup.core.json.SchemaRoundTripTest
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.perfetto.TraceGoldens
import io.embrace.startup.perfetto.TraceHealth
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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

    private val goldens: Path = SchemaRoundTripTest.fixturesRoot().toPath().resolve("goldens")
    private val longitudinal: Path = SchemaRoundTripTest.fixturesRoot().toPath().resolve("longitudinal")

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

    @Test
    fun `trace-health CLI summary and per-trace lines match the golden on the fixture traces`() {
        val traces = TraceGoldens.all()
        assumeTrue(traces.isNotEmpty())
        traces.groupBy { it.device }.forEach { (device, goldensForDevice) ->
            val want = TraceGoldens.cliStdout("trace_health.$device.stdout.txt") ?: return@forEach
            val reports = goldensForDevice.sortedBy { it.traceFileName }.map { g ->
                TraceHealth.evaluate(g.traceFileName, TraceHealth.parseRows(g.csv("health")))
            }
            val lines = reports.filter { it.verdict != TraceHealth.Verdict.OK }
                .map { TraceHealthCommand.perTraceLine(it, Path.of(it.trace), showMeta = false) } +
                TraceHealth.summarize(reports).lines()
            assertEquals("$device trace-health", want.trimEnd(), lines.joinToString("\n").trimEnd())
        }
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
