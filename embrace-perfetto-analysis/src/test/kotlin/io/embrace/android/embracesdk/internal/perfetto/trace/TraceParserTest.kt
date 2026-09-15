package io.embrace.android.embracesdk.internal.perfetto.trace

import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEvent
import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEventBundle
import io.embrace.android.embracesdk.internal.perfetto.proto.PrintFtraceEvent
import io.embrace.android.embracesdk.internal.perfetto.proto.Trace
import io.embrace.android.embracesdk.internal.perfetto.proto.TracePacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class TraceParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `a gzipped trace decodes to the packets it was written with`() {
        val written = trace(bundle(print(1000, "B|$TID|section"), print(1500, "E|$TID")))
        assertEquals(written, parseTrace(gzipped("trace.gz", written)))
    }

    @Test
    fun `the containers a trace arrives in all decode to the same trace`() {
        val written = trace(bundle(print(1000, "B|$TID|section"), print(1500, "E|$TID")))
        assertEquals(written, parseTrace(bundled("bundle.perfetto-trace", "Trace_output.pb", written)))
        assertEquals(written, parseTrace(gzipped("trace.gz", written)))
        assertEquals(written, parseTrace(raw("trace.pb", written)))
    }

    @Test
    fun `a bundle is read by the file it holds rather than the name that file was given`() {
        val written = trace(bundle(print(1000, "B|$TID|section")))
        assertEquals(written, parseTrace(bundled("renamed.perfetto-trace", "other_name.pb", written)))
    }

    @Test
    fun `a bundle holding nothing is rejected rather than read as an empty trace`() {
        val empty = tmp.newFile("empty.perfetto-trace").apply { ZipOutputStream(outputStream()).use { } }
        val exc = assertThrows(IOException::class.java) { parseTrace(empty) }
        assertTrue(exc.message, exc.message?.contains("empty.perfetto-trace") == true)
    }

    @Test
    fun `events are gathered from every bundle, in the order the trace holds them`() {
        val inner = print(1100, "B|$TID|inner")
        val outer = print(1000, "B|$TID|outer")
        val events = ftraceEvents(
            Trace(
                packet = listOf(
                    TracePacket(ftrace_events = bundle(inner)),
                    TracePacket(ftrace_events = bundle(outer)),
                ),
            ),
        )
        assertEquals(listOf(inner, outer), events)
    }

    @Test
    fun `a trace carrying no ftrace events yields none`() {
        assertEquals(emptyList<FtraceEvent>(), ftraceEvents(Trace()))
        assertEquals(emptyList<FtraceEvent>(), ftraceEvents(Trace(packet = listOf(TracePacket()))))
        assertEquals(emptyList<FtraceEvent>(), ftraceEvents(trace(FtraceEventBundle())))
    }

    @Test
    fun `ftrace events that atrace did not write are separated from those it did`() {
        val atrace = print(1000, "B|$TID|section")
        val other = FtraceEvent(timestamp = 1100, pid = TID)
        assertEquals(listOf(atrace), printEvents(listOf(atrace, other)))
    }

    @Test
    fun `the window spans events atrace did not write, not just the ones it did`() {
        val events = listOf(
            FtraceEvent(timestamp = 500, pid = TID),
            print(1000, "B|$TID|section"),
            print(1500, "E|$TID"),
            FtraceEvent(timestamp = 3000, pid = TID),
        )
        assertEquals(2500L, traceWindowNanos(events))
        assertEquals(500L, traceWindowNanos(printEvents(events)))
    }

    @Test
    fun `the window spans the earliest event to the latest, whatever order they arrive in`() {
        val ordered = listOf(print(1000, "B|$TID|section"), print(1500, "E|$TID"), print(9000, "E|$TID"))
        assertEquals(8000L, traceWindowNanos(ordered))
        assertEquals(8000L, traceWindowNanos(ordered.reversed()))
        assertEquals(8000L, traceWindowNanos(listOf(ordered[1], ordered[2], ordered[0])))
    }

    @Test
    fun `a trace with nothing to span reports no window rather than failing`() {
        assertEquals(0L, traceWindowNanos(emptyList()))
        assertEquals(0L, traceWindowNanos(listOf(print(1000, "B|$TID|section"))))
    }

    @Test
    fun `a file that is not a readable trace is rejected rather than read as empty`() {
        val raw = tmp.newFile("raw").apply { writeBytes(byteArrayOf(0x0a, 0x55, 0x32, 0x4e)) }
        assertThrows(IOException::class.java) { parseTrace(raw) }

        val truncated = tmp.newFile("truncated.gz").apply {
            val bytes = trace(bundle(print(1000, "B|$TID|section"))).encode()
            GZIPOutputStream(outputStream()).use { it.write(bytes, 0, bytes.size - 2) }
        }
        val exc = assertThrows(IOException::class.java) { parseTrace(truncated) }
        assertTrue(exc.message, exc.message?.contains("truncated.gz") == true)
    }

    private fun trace(vararg bundles: FtraceEventBundle) =
        Trace(packet = bundles.map { TracePacket(ftrace_events = it) })

    private fun bundle(vararg events: FtraceEvent) = FtraceEventBundle(event = events.toList())

    private fun print(timestamp: Long, payload: String) =
        FtraceEvent(timestamp = timestamp, pid = TID, print = PrintFtraceEvent(buf = payload))

    private fun gzipped(name: String, content: Trace): File = tmp.newFile(name).apply {
        GZIPOutputStream(outputStream()).use { it.write(content.encode()) }
    }

    private fun raw(name: String, content: Trace): File = tmp.newFile(name).apply { writeBytes(content.encode()) }

    private fun bundled(name: String, entry: String, content: Trace): File = tmp.newFile(name).apply {
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(entry))
            zip.write(content.encode())
            zip.closeEntry()
        }
    }

    private companion object {
        const val TID = 9874
    }
}
