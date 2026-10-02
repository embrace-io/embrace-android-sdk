package io.embrace.analysis.reports

import io.embrace.analysis.common.text.Csv
import io.embrace.analysis.fixtures.TraceGoldens
import io.embrace.analysis.perfetto.Prebuilt
import io.embrace.analysis.perfetto.TraceHealth
import io.embrace.analysis.perfetto.TraceProcessor
import io.embrace.analysis.records.store.IngestQueries
import io.embrace.analysis.records.store.StartupHealth
import io.embrace.analysis.records.store.TraceReads
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

/**
 * The golden-parity gate for the trace_processor query client, in two layers.
 *
 * Offline (always runs when the goldens are present): the saved stdout of every query parses into
 * the rows the golden recorded - `(what, k, val)` triples after the header sentinel, the window value
 * and the signal inventory exactly as the golden extracted them.
 *
 * Live (only with `./gradlew -PtraceParity=1`, or `STARTUP_TOOLS_TRACE_PARITY=1`, the captured traces
 * on disk and a resolvable native `trace_processor_shell`): every query is re-run through this client
 * on every fixture trace and its parsed rows compared with the frozen golden's. This is the Layer-B
 * gate proper; it re-parses each trace nine times, so it is opt-in.
 *
 * The generic, golden-free unit tests for [TraceProcessor] and the rest of the perfetto module's
 * public surface live in `TraceProcessorTest` in `embrace-analysis-perfetto`.
 */
class TraceParityTest {

    @Test
    fun `saved query output parses into what the golden recorded, on every frozen trace`() {
        val goldens = TraceGoldens.all()
        assumeTrue("trace goldens not present", goldens.isNotEmpty())
        goldens.forEach { golden ->
            val parsed = golden.parsed()
            val triples = TraceProcessor.triplesOf(Csv.parse(golden.csv("startup_metrics")))
            val window = triples.single { it.what == "window_ms" }.value
            assertNotNull("${golden.stem} window_ms", window)
            assertEquals("${golden.stem} window_source", "emb-sdk-start", triples.single { it.what == "window_source" }.k)
            assertTrue("${golden.stem} sections", triples.count { it.what == "section" } > 20)

            val wantWindow = parsed.getValue("window_emb_sdk_start_ms")
            val gotWindow = TraceReads.parseWindow(golden.csv("window_emb_sdk_start"))
            if (wantWindow is JsonNull) {
                assertNull(gotWindow)
            } else {
                assertEquals("${golden.stem} window", wantWindow.jsonPrimitive.double, gotWindow!!, 0.0)
                // The ingest window is the analyze window: same slice, same engine.
                assertEquals("${golden.stem} window agrees", window!!.toDouble(), gotWindow, 0.0)
            }
            val wantComposed = parsed.getValue("window_composed_ms")
            val gotComposed = TraceReads.parseWindow(golden.csv("window_composed"))
            if (wantComposed is JsonNull) {
                assertNull(gotComposed)
            } else {
                assertEquals("${golden.stem} composed", wantComposed.jsonPrimitive.double, gotComposed!!, 0.0)
            }
            val wantSignals = parsed.getValue("signals").jsonArray.map { it.jsonPrimitive.content }
            assertEquals("${golden.stem} signals", wantSignals, TraceReads.parseSignals(golden.csv("signals")))
        }
    }

    @Test
    fun `live - the native engine reproduces the launcher's rows for every query on every fixture trace`() {
        val optedIn = System.getenv("STARTUP_TOOLS_TRACE_PARITY") == "1" || System.getProperty("startup.traceParity") == "1"
        assumeTrue("run with ./gradlew -PtraceParity=1 (or STARTUP_TOOLS_TRACE_PARITY=1)", optedIn)
        val goldens = TraceGoldens.all()
        assumeTrue("trace goldens not present", goldens.isNotEmpty())
        val repo = TraceGoldens.repoRoot()
        val available = goldens.mapNotNull { g -> TraceGoldens.traceFile(g, repo)?.let { g to it } }
        assumeTrue("captured traces not on this machine", available.isNotEmpty())
        val binary = runCatching { Prebuilt.resolve(repoRoot = repo) }.getOrNull()
        assumeTrue("no native trace_processor_shell resolvable", binary != null && Files.isExecutable(binary))
        val tp = TraceProcessor(binary!!)

        val sqlByQuery = mapOf(
            "startup_metrics" to StartupQueries.STARTUP_METRICS,
            "init_window_sched" to StartupQueries.INIT_WINDOW_SCHED,
            "variance_metrics" to StartupQueries.VARIANCE_METRICS,
            "outlier_metrics" to StartupQueries.OUTLIER_METRICS,
            "foreign_gc_overlap" to StartupQueries.FOREIGN_GC_OVERLAP,
            "health" to TraceHealth.sql(StartupHealth.CANARY),
            "window_emb_sdk_start" to IngestQueries.window("emb-sdk-start"),
            "window_composed" to IngestQueries.COMPOSED_WINDOW,
            "signals" to IngestQueries.SIGNALS,
        )
        assertEquals(TraceGoldens.QUERY_NAMES.toSet(), sqlByQuery.keys)
        available.forEach { (golden, trace) ->
            sqlByQuery.forEach { (name, sql) ->
                val want = Csv.parse(golden.csv(name))
                val got = tp.rows(sql, trace)
                assertEquals("${golden.device}/${golden.stem} $name", want, got)
            }
        }
    }

    @Test
    fun `live - a warm session answers every query with the same rows as the cold path, once per device`() {
        val optedIn = System.getenv("STARTUP_TOOLS_TRACE_PARITY") == "1" || System.getProperty("startup.traceParity") == "1"
        assumeTrue("run with ./gradlew -PtraceParity=1", optedIn)
        val goldens = TraceGoldens.all()
        val repo = TraceGoldens.repoRoot()
        val perDevice = goldens.groupBy {
            it.device
        }.values.mapNotNull { g ->
            g.firstNotNullOfOrNull { c ->
                TraceGoldens.traceFile(
                    c,
                    repo,
                )?.let { c to it }
            }
        }
        assumeTrue("captured traces not on this machine", perDevice.isNotEmpty())
        val binary = runCatching { Prebuilt.resolve(repoRoot = repo) }.getOrNull()
        assumeTrue("no native trace_processor_shell resolvable", binary != null)
        val tp = TraceProcessor(binary!!)
        perDevice.forEach { (golden, trace) ->
            val started = System.nanoTime()
            tp.withWarmTrace(trace) { target ->
                listOf(
                    "startup_metrics" to StartupQueries.STARTUP_METRICS,
                    "health" to TraceHealth.sql(StartupHealth.CANARY),
                    "window_emb_sdk_start" to IngestQueries.window("emb-sdk-start"),
                    "signals" to IngestQueries.SIGNALS,
                ).forEach { (name, sql) ->
                    assertEquals("${golden.device} warm $name", Csv.parse(golden.csv(name)), Csv.parse(target.queryRaw(sql).stdout))
                }
            }
            println("warm session, ${golden.device}: 4 queries in ${(System.nanoTime() - started) / 1e9} s (one parse)")
        }
    }
}
