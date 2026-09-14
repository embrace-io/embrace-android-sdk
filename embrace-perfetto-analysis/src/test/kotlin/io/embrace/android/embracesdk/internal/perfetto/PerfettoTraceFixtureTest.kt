package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reads the committed macrobenchmark capture. Every expected value was measured from that file, so
 * regenerating it means updating them.
 */
internal class PerfettoTraceFixtureTest {

    private val trace = parseTrace(fixture())
    private val events = ftraceEvents(trace)

    @Test
    fun `the capture decodes to the ftrace events it holds`() {
        assertEquals(32, trace.packet.size)
        assertEquals(2188, events.size)
        assertEquals(2188, printEvents(events).size)
    }

    @Test
    fun `the process stats name the threads atrace wrote from, which atrace itself does not`() {
        val names = threadNames(trace)
        assertEquals(
            listOf(
                "io.embrace.android.embracesdk.macrobenchmark.app",
                "emb-io-reg",
                "emb-http-reques",
                "emb-non-io-reg",
                "emb-session-per",
                "emb-data-persis",
            ),
            listOf(9874, 9891, 9892, 9894, 9904, 9905).map(names::get),
        )
    }

    @Test
    fun `a thread the capture named nothing for is absent, rather than present and blank`() {
        assertNull(threadNames(trace)[999_999])
    }

    @Test
    fun `fields the trimmed schema does not declare are skipped rather than failing the decode`() {
        assertTrue("no packet carried an undeclared field", trace.packet.any { it.unknownFields.size > 0 })
    }

    @Test
    fun `atrace wrote from every thread the benchmark ran on`() {
        assertEquals(listOf(9874, 9891, 9892, 9894, 9904, 9905), events.map(FtraceEvent::pid).distinct().sorted())
        assertEquals(1892, events.count { it.pid == 9874 })
    }

    @Test
    fun `the payloads are atrace begin and end events, carrying the sections the sdk emits`() {
        val payloads = events.mapNotNull { it.print?.buf?.substringBefore('\n') }
        assertTrue(payloads.all { it.startsWith("B|") || it == "E" || it.startsWith("E|") })
        assertEquals(1094, payloads.count { it.startsWith("B|") })
        assertTrue(payloads.any { it.endsWith("|emb-sdk-start") })
    }

    @Test
    fun `timestamps are not in trace order, since ftrace batches per cpu`() {
        val timestamps = events.map(FtraceEvent::timestamp)
        assertTrue("the trace happened to be sorted", timestamps != timestamps.sorted())
    }

    private fun fixture(): File {
        val resource = checkNotNull(javaClass.getResource("/$FIXTURE")) { "missing test resource $FIXTURE" }
        return File(resource.toURI())
    }

    private companion object {
        const val FIXTURE = "macrobenchmark-session-multi-file.perfetto.gz"
    }
}
