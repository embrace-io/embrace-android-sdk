package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEvent
import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEventBundle
import io.embrace.android.embracesdk.internal.perfetto.proto.PrintFtraceEvent
import io.embrace.android.embracesdk.internal.perfetto.proto.Trace
import io.embrace.android.embracesdk.internal.perfetto.proto.TracePacket
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
    fun `no statistics are asked for by default`() {
        val options = checkNotNull(parseArgs(arrayOf(TRACE)))
        assertEquals(emptyList<String>(), options.operations)
        assertFalse(options.allOperations)
        assertFalse(options.reportsStats)
        assertEquals(ReportFormat.MARKDOWN, options.format)
    }

    @Test
    fun `sections are named as one comma separated list, or asked for wholesale`() {
        val named = checkNotNull(parseArgs(arrayOf(TRACE, "--operations", "emb-sdk-start,emb-core-init")))
        assertEquals(listOf("emb-sdk-start", "emb-core-init"), named.operations)
        assertTrue(named.reportsStats)

        val all = checkNotNull(parseArgs(arrayOf("--all-operations", "--format", "json", TRACE)))
        assertEquals(File(TRACE), all.trace)
        assertTrue(all.allOperations)
        assertEquals(ReportFormat.JSON, all.format)
    }

    @Test
    fun `an option whose value is missing, unknown, or contradictory is a usage error`() {
        listOf(
            arrayOf(TRACE, "--format"),
            arrayOf(TRACE, "--format", "xml"),
            arrayOf(TRACE, "--operations"),
            arrayOf(TRACE, "--operations", "--format", "json"),
            arrayOf(TRACE, "--operations", "emb-sdk-start,"),
            arrayOf(TRACE, "--operations", "emb-sdk-start", "--all-operations"),
            arrayOf(TRACE, "--all-operations", "--operations", "emb-sdk-start"),
        ).forEach { args ->
            assertNull(args.joinToString(" "), parseArgs(args))
        }
    }

    @Test
    fun `the format decides the document, which is the whole of what a statistics run prints`() {
        val report = StatsReport("t.gz", 2048, 12, 3, 2, 1_200_000, TraceStats(emptyList(), emptyList()))
        assertTrue(render(ReportFormat.MARKDOWN, report).startsWith("# Perfetto trace statistics"))
        assertTrue(render(ReportFormat.JSON, report).startsWith("{"))
    }

    @Test
    fun `a report measures the sections asked for, names the rest as missing, and sorts them all`() {
        val events = trace(
            print(1000, "B|$TID|emb-zeta", tid = TID),
            print(1100, "E|$TID", tid = TID),
            print(1200, "B|$TID|emb-alpha", tid = TID),
            print(1300, "E|$TID", tid = TID),
        )
        val named = statsReport(Options(File(TRACE), operations = listOf("emb-zeta", "emb-absent")), events)
        assertEquals(listOf("emb-zeta"), named.stats.operations.map(OperationStats::name))
        assertEquals(listOf("emb-absent"), named.stats.missing)
        assertEquals(2, named.sliceCount)
        assertEquals(2, named.sectionCount)
        assertEquals(1, named.threadCount)

        val all = statsReport(Options(File(TRACE), allOperations = true), events)
        assertEquals(listOf("emb-alpha", "emb-zeta"), all.stats.operations.map(OperationStats::name))
    }

    @Test
    fun `the window a report measures shares against runs to events atrace never wrote`() {
        val events = trace(
            print(1000, "B|$TID|emb-zeta", tid = TID),
            print(2000, "E|$TID", tid = TID),
            FtraceEvent(timestamp = 5000, pid = TID),
        )
        val report = statsReport(Options(File(TRACE), operations = listOf("emb-zeta")), events)
        assertEquals(4000L, report.traceWindowNanos)
        assertEquals(25.0, report.stats.operations.single().traceWindowPercent, 0.0)
    }

    @Test
    fun `the inputs are reported before anything is read`() {
        val text = describe(Options(File("a.perfetto-trace"), dryRun = true), TraceFormat.PERFETTO)
        assertTrue(text, text.contains("trace: a.perfetto-trace"))
        assertTrue(text, text.contains("format: ${TraceFormat.PERFETTO.label}"))
    }

    @Test
    fun `the summary counts what was decoded, including the threads atrace wrote from`() {
        val text = summarise(
            Trace(
                packet = listOf(
                    TracePacket(
                        ftrace_events = FtraceEventBundle(
                            event = listOf(
                                print(1000, "B|$TID|emb-sdk-start", tid = TID),
                                print(1100, "B|$OTHER_TID|emb-core-init", tid = OTHER_TID),
                                FtraceEvent(timestamp = 1200, pid = TID),
                            ),
                        ),
                    ),
                    TracePacket(ftrace_events = FtraceEventBundle(event = listOf(print(1300, "E|$TID", tid = TID)))),
                ),
            ),
        )

        assertTrue(text, text.contains("packets: 2"))
        assertTrue(text, text.contains("ftrace events: 4"))
        assertTrue(text, text.contains("atrace events: 3 across 2 threads"))
    }

    @Test
    fun `the summary reports the slices the events paired into, and what did not pair`() {
        val text = summarise(
            trace(
                print(1000, "B|$TID|emb-sdk-start", tid = TID),
                print(1300, "E|$TID", tid = TID),
                print(1100, "B|$OTHER_TID|emb-core-init", tid = OTHER_TID),
            ),
        )

        assertTrue(text, text.contains("slices: 1 of 1 distinct sections"))
        assertTrue(text, text.contains("tid $TID: 1 slices"))
        assertTrue(text, text.contains("skipped: 1 unclosed, 0 unopened, 0 unsupported"))
    }

    @Test
    fun `an empty trace summarises as empty rather than failing`() {
        val text = summarise(Trace())

        assertTrue(text, text.contains("packets: 0"))
        assertTrue(text, text.contains("ftrace events: 0"))
        assertTrue(text, text.contains("atrace events: 0 across 0 threads"))
        assertTrue(text, text.contains("slices: 0 of 0 distinct sections"))
    }

    private fun trace(vararg events: FtraceEvent) =
        Trace(packet = listOf(TracePacket(ftrace_events = FtraceEventBundle(event = events.toList()))))

    private fun print(timestamp: Long, payload: String, tid: Int) =
        FtraceEvent(timestamp = timestamp, pid = tid, print = PrintFtraceEvent(buf = payload))

    private companion object {
        const val TID = 9874
        const val OTHER_TID = 9892
        const val TRACE = "a.perfetto-trace"
    }
}
