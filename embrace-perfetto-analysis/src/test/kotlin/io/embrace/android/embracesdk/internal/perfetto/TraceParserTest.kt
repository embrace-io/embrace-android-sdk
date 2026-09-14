package io.embrace.android.embracesdk.internal.perfetto

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

internal class TraceParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `a gzipped trace decodes to the packets it was written with`() {
        val written = trace(bundle(print(1000, "B|$TID|section"), print(1500, "E|$TID")))
        assertEquals(written, parseTrace(gzipped("trace.gz", written)))
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

    private companion object {
        const val TID = 9874
    }
}
