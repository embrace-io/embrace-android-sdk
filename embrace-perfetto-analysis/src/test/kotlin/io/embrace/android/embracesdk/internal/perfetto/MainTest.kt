package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.cli.CliOptions
import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEvent
import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEventBundle
import io.embrace.android.embracesdk.internal.perfetto.proto.PrintFtraceEvent
import io.embrace.android.embracesdk.internal.perfetto.proto.Trace
import io.embrace.android.embracesdk.internal.perfetto.proto.TracePacket
import io.embrace.android.embracesdk.internal.perfetto.report.ReportFormat
import io.embrace.android.embracesdk.internal.perfetto.report.render
import io.embrace.android.embracesdk.internal.perfetto.report.renderHtml
import io.embrace.android.embracesdk.internal.perfetto.stats.OperationStats
import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.TraceStats
import io.embrace.android.embracesdk.internal.perfetto.trace.TraceFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

internal class MainTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the format decides the document, which is the whole of what a statistics run writes`() {
        val report = StatsReport("t.gz", 2048, 12, 3, 2, 1_200_000, TraceStats(emptyList(), emptyList(), emptyList()))
        assertTrue(render(ReportFormat.MARKDOWN, report).startsWith("# Perfetto trace statistics"))
        assertTrue(render(ReportFormat.JSON, report).startsWith("{"))
        assertTrue(render(ReportFormat.HTML, report).startsWith("<!doctype html>"))
    }

    @Test
    fun `a statistics run writes the document to the file it was given, and reports where that went`() {
        val events = trace(print(1000, "B|$TID|emb-zeta", tid = TID), print(1100, "E|$TID", tid = TID))
        val output = File(folder.root, "report.html")
        val options = options(operations = listOf("emb-zeta"), format = ReportFormat.HTML, output = output)

        assertEquals("wrote html statistics to ${output.path}", writeStats(options, events))
        assertEquals(renderHtml(statsReport(options, events)), output.readText())
    }

    @Test
    fun `a document that cannot be written fails rather than being dropped`() {
        val options = options(output = File(folder.root, "absent/report"))
        assertThrows(IOException::class.java) { writeStats(options, Trace()) }
    }

    @Test
    fun `a report measures the sections asked for, names the rest as missing, and sorts them all`() {
        val events = trace(
            print(1000, "B|$TID|emb-zeta", tid = TID),
            print(1100, "E|$TID", tid = TID),
            print(1200, "B|$TID|emb-alpha", tid = TID),
            print(1300, "E|$TID", tid = TID),
        )
        val named = statsReport(options(operations = listOf("emb-zeta", "emb-absent")), events)
        assertEquals(listOf("emb-zeta"), named.stats.operations.map(OperationStats::name))
        assertEquals(listOf("emb-absent"), named.stats.missing)
        assertEquals(2, named.sliceCount)
        assertEquals(2, named.sectionCount)
        assertEquals(1, named.threadCount)

        val all = statsReport(options(), events)
        assertEquals(listOf("emb-alpha", "emb-zeta"), all.stats.operations.map(OperationStats::name))
    }

    @Test
    fun `the window a report measures shares against runs to events atrace never wrote`() {
        val events = trace(
            print(1000, "B|$TID|emb-zeta", tid = TID),
            print(2000, "E|$TID", tid = TID),
            FtraceEvent(timestamp = 5000, pid = TID),
        )
        val report = statsReport(options(operations = listOf("emb-zeta")), events)
        assertEquals(4000L, report.traceWindowNanos)
        assertEquals(25.0, report.stats.operations.single().traceWindowPercent, 0.0)
    }

    @Test
    fun `the inputs are reported before anything is read`() {
        val text = describe(options(format = ReportFormat.JSON), TraceFormat.PERFETTO)
        assertTrue(text, text.contains("trace: $TRACE"))
        assertTrue(text, text.contains("format: ${TraceFormat.PERFETTO.label}"))
        assertTrue(text, text.contains("report: $OUTPUT (json)"))
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
    fun `the summary reports the counter samples the events carried, named and counted`() {
        val text = summarise(
            trace(
                print(1000, "C|$TID|emb-sf-bytes-written|4096", tid = TID),
                print(1100, "C|$TID|emb-sf-files-written|1", tid = TID),
                print(1200, "C|$TID|emb-sf-bytes-written|8192", tid = OTHER_TID),
            ),
        )
        assertTrue(text, text.contains("counters: 3 samples of 2 distinct counters"))
        assertTrue(text, text.contains("skipped: 0 unclosed, 0 unopened, 0 unsupported"))
    }

    @Test
    fun `an empty trace summarises as empty rather than failing`() {
        val text = summarise(Trace())

        assertTrue(text, text.contains("packets: 0"))
        assertTrue(text, text.contains("ftrace events: 0"))
        assertTrue(text, text.contains("atrace events: 0 across 0 threads"))
        assertTrue(text, text.contains("slices: 0 of 0 distinct sections"))
        assertTrue(text, text.contains("counters: 0 samples of 0 distinct counters"))
    }

    private fun options(
        operations: List<String> = emptyList(),
        format: ReportFormat = ReportFormat.MARKDOWN,
        output: File = File(OUTPUT),
    ) = CliOptions(inputs = listOf(File(TRACE)), operations = operations, format = format, output = output)

    private fun trace(vararg events: FtraceEvent) =
        Trace(packet = listOf(TracePacket(ftrace_events = FtraceEventBundle(event = events.toList()))))

    private fun print(timestamp: Long, payload: String, tid: Int) =
        FtraceEvent(timestamp = timestamp, pid = tid, print = PrintFtraceEvent(buf = payload))

    private companion object {
        const val TID = 9874
        const val OTHER_TID = 9892
        const val TRACE = "a.perfetto-trace"
        const val OUTPUT = "report.json"
    }
}
