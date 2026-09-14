package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEvent
import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEventBundle
import io.embrace.android.embracesdk.internal.perfetto.proto.PrintFtraceEvent
import io.embrace.android.embracesdk.internal.perfetto.proto.Trace
import io.embrace.android.embracesdk.internal.perfetto.proto.TracePacket
import org.junit.Assert.assertEquals
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
    fun `an empty trace summarises as empty rather than failing`() {
        val text = summarise(Trace())

        assertTrue(text, text.contains("packets: 0"))
        assertTrue(text, text.contains("ftrace events: 0"))
        assertTrue(text, text.contains("atrace events: 0 across 0 threads"))
    }

    private fun print(timestamp: Long, payload: String, tid: Int) =
        FtraceEvent(timestamp = timestamp, pid = tid, print = PrintFtraceEvent(buf = payload))

    private companion object {
        const val TID = 9874
        const val OTHER_TID = 9892
    }
}
