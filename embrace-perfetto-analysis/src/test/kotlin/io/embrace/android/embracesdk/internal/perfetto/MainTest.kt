package io.embrace.android.embracesdk.internal.perfetto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

internal class MainTest {

    @Test
    fun `a trace path is parsed, with dry run off by default`() {
        assertEquals(Options(File("a.perfetto-trace"), dryRun = false), parseArgs(arrayOf("a.perfetto-trace")))
        assertEquals(
            Options(File("a.perfetto-trace"), dryRun = true),
            parseArgs(arrayOf("a.perfetto-trace", "--dry-run")),
        )
    }

    @Test
    fun `arguments that do not name exactly one trace are rejected`() {
        listOf(
            arrayOf(),
            arrayOf("--dry-run"),
            arrayOf("a.perfetto-trace", "b.perfetto-trace"),
            arrayOf("--unknown", "a.perfetto-trace"),
        ).forEach { args ->
            assertNull(args.joinToString(" "), parseArgs(args))
        }
    }

    @Test
    fun `the inputs are reported before anything is read`() {
        val text = describe(Options(File("a.perfetto-trace"), dryRun = true), TraceFormat.PERFETTO)
        assertTrue(text, text.contains("trace: a.perfetto-trace"))
        assertTrue(text, text.contains("format: ${TraceFormat.PERFETTO.label}"))
    }

    @Test
    fun `the summary counts the slices and names the slowest sections`() {
        val text = summarise(
            PerfettoTrace(
                listOf(
                    TraceSlice("emb-sdk-start", 1, 0, 3_000_000, 0),
                    TraceSlice("emb-core-init", 1, 0, 2_000_000, 1),
                    TraceSlice("emb-core-init", 2, 0, 500_000, 0),
                    TraceSlice("session-workload", 2, 0, 9_000_000, 0),
                ),
            ),
        )

        assertTrue(text, text.contains("slices: 4 across 2 threads"))
        assertTrue(text, text.contains("span: 9.00 ms"))
        assertTrue(text, text.contains("$EMB_PREFIX sections: 3"))
        assertTrue(text, text.contains("emb-core-init: 2.50 ms over 2 calls"))
        assertTrue(text, text.contains("emb-sdk-start: 3.00 ms over 1 call"))

        // only the sdk's own sections are ranked
        assertTrue(text, !text.contains("session-workload"))
    }

    @Test
    fun `anomalies are reported so a truncated trace is not read as a clean one`() {
        val clean = PerfettoTrace(emptyList())
        assertFalse(clean.hasAnomalies)

        val truncated = PerfettoTrace(emptyList(), unmatchedEndCount = 3)
        assertTrue(truncated.hasAnomalies)
        assertTrue(warning(truncated), warning(truncated).contains("3 unmatched end events"))
    }
}
