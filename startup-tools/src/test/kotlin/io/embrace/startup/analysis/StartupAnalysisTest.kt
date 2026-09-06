package io.embrace.startup.analysis

import io.embrace.startup.core.text.Csv
import io.embrace.startup.core.text.PyFormat
import io.embrace.startup.perfetto.TraceGoldens
import io.embrace.startup.perfetto.TraceProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The `analyze` report against the Python's frozen stdout per device: metrics are rebuilt from the
 * saved `startup_metrics.sql` output of every trace (in `iterNNN` order, as the Python listed them),
 * rendered, and compared line for line - excluding only the two header lines that carry the start
 * time and the absolute traces directory, and the trailing "summary written to" line.
 */
class StartupAnalysisTest {

    @Test
    fun `Python format parity - half-even on the exact binary value`() {
        assertEquals("0.12", PyFormat.fixed(0.125, 2))
        assertEquals("0.38", PyFormat.fixed(0.375, 2))
        assertEquals("2.67", PyFormat.fixed(2.675, 2)) // 2.675 is below the tie in binary, as in Python
        assertEquals("15%", PyFormat.percent0(0.15))
        assertEquals("-0.0", PyFormat.fixed(-0.04, 1))
        assertEquals(26.16, PyFormat.exactMean(listOf(26.15, 26.17)), 0.0)
    }

    @Test
    fun `iteration ordering and short names follow the Python regexes`() {
        assertEquals(7, StartupAnalysis.iterIndex("StartupBenchmarks_cold_iter007_2026.perfetto-trace"))
        assertEquals(1 shl 30, StartupAnalysis.iterIndex("no-index.perfetto-trace"))
        assertEquals("iter007_2026-09-02", StartupAnalysis.short("StartupBenchmarks_cold_iter007_2026-09-02.perfetto-trace"))
        assertEquals("plain.perfetto-trace", StartupAnalysis.short("plain.perfetto-trace"))
    }

    @Test
    fun `the report reproduces analyze_startup py stdout for every fixture device`() {
        val goldens = TraceGoldens.all()
        assumeTrue("trace goldens not present", goldens.isNotEmpty())
        val byDevice = goldens.groupBy { it.device }
        var compared = 0
        byDevice.forEach { (device, traces) ->
            val want = TraceGoldens.cliStdout("analyze_startup.$device.stdout.txt") ?: return@forEach
            val perTrace = traces.sortedBy { StartupAnalysis.iterIndex(it.traceFileName) }.map { g ->
                g.traceFileName to StartupAnalysis.metricsOf(TraceProcessor.triplesOf(Csv.parse(g.csv("startup_metrics"))))
            }
            val got = StartupAnalysis.report(perTrace)
            val wantBody = want.lines().drop(2).dropLastWhile { it.isEmpty() }.dropLast(1).dropLastWhile { it.isEmpty() }
            val gotBody = got.lines().dropLastWhile { it.isEmpty() }
            assertEquals("$device report", wantBody.joinToString("\n"), gotBody.joinToString("\n"))
            compared++
        }
        assumeTrue("no analyze_startup CLI goldens captured yet", compared > 0)
        assertTrue(compared > 0)
    }
}
