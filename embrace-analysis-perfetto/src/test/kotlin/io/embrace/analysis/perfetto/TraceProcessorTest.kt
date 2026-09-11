package io.embrace.analysis.perfetto

import io.embrace.analysis.common.text.Csv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The engine-agnostic pieces of the perfetto module's public surface that need no golden fixture:
 * [TraceProcessor.triplesOf]'s row parsing and [TraceProcessor.QueryFailed]'s cause, [TraceHealth]'s
 * trace discovery and per-trace line formatting, and [Queries]' SQL text. The golden-parity gate that
 * needs the startup SQL and the captured fixture traces is `TraceParityTest`, in `embrace-analysis-reports`.
 */
class TraceProcessorTest {

    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("trace-processor-test")
    }

    @Test
    fun `triples skip everything before the header and drop malformed rows`() {
        val rows = Csv.parse("\n\"what\",\"k\",\"val\"\n\"section\",\"emb-x\",1.5\n\"odd\"\n\"ttid_ms\",\"\",[NULL]\n")
        val triples = TraceProcessor.triplesOf(rows)
        assertEquals(
            listOf(TraceProcessor.Triple("section", "emb-x", "1.5"), TraceProcessor.Triple("ttid_ms", "", null)),
            triples,
        )
    }

    @Test(expected = TraceProcessor.QueryFailed::class)
    fun `a missing header is an error, not an empty result`() {
        TraceProcessor.triplesOf(Csv.parse("\"k\",\"v\"\n\"canary\",1\n"))
    }

    @Test
    fun `QueryFailed carries its cause when given one`() {
        val cause = IOException("native process vanished")

        val withCause = TraceProcessor.QueryFailed("trace_processor failed", cause)
        val withoutCause = TraceProcessor.QueryFailed("trace_processor failed")

        assertSame(cause, withCause.cause)
        assertNull(withoutCause.cause)
    }

    @Test
    fun `listTraces returns every perfetto-trace sorted before every pftrace sorted, recursively`() {
        val level1 = Files.createDirectories(tempDir.resolve("level1"))
        val level2 = Files.createDirectories(tempDir.resolve("level2/sub"))
        val b = Files.writeString(level1.resolve("b.perfetto-trace"), "b")
        val a = Files.writeString(level1.resolve("a.perfetto-trace"), "a")
        val z = Files.writeString(level2.resolve("z.pftrace"), "z")
        val m = Files.writeString(level2.resolve("m.pftrace"), "m")
        Files.writeString(tempDir.resolve("readme.txt"), "not a trace")

        assertEquals(listOf(a, b, m, z), TraceHealth.listTraces(tempDir))
    }

    @Test
    fun `perTraceLine pads the verdict, sorts counters by key, shows meta only when asked, and flags a burst only when present`() {
        val trace = Path.of("mytrace.perfetto-trace")
        val rows = mapOf(
            "canary" to 3L,
            "loss.zzz_packets_lost" to 7L,
            "loss.aaa_mystery" to 4L,
            "loss.mm_unknown_type" to 9L,
        )
        val bursting = TraceHealth.evaluate("t", rows, classLoadsInBurstSection = 119L, burstThreshold = 80L)
        val quiet = TraceHealth.evaluate("t", rows, classLoadsInBurstSection = 1L, burstThreshold = 80L)

        val line = TraceHealth.perTraceLine(bursting, trace, showMeta = false)

        assertTrue(line, line.contains("[${bursting.verdict.key.padEnd(17)}]"))
        assertTrue(line, line.contains("mytrace.perfetto-trace"))
        assertTrue(line, line.contains("canary=3"))
        assertTrue(line, line.contains("aaa_mystery=4, zzz_packets_lost=7"))
        assertFalse(line, line.contains("mm_unknown_type"))
        assertTrue(line, line.contains("burst-section-classloads=119"))

        val withMeta = TraceHealth.perTraceLine(bursting, trace, showMeta = true)
        assertTrue(withMeta, withMeta.contains("mm_unknown_type=9"))

        val noBurstLine = TraceHealth.perTraceLine(quiet, trace, showMeta = false)
        assertFalse(noBurstLine, noBurstLine.contains("burst-section-classloads"))
    }
}
