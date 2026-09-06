package io.embrace.startup.perfetto

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Trace-health verdicts: the bucket table and every verdict branch on synthetic counters, then the
 * Python's own verdict on every frozen fixture trace (`python_parsed.json > health_check_trace`)
 * reproduced from the saved health-query stdout.
 */
class TraceHealthTest {

    @Test
    fun `counters bucket by what they can invalidate, unknown ones as parse`() {
        assertEquals(TraceHealth.Bucket.BUFFER, TraceHealth.classify("traced_buf_chunks_overwritten"))
        assertEquals(TraceHealth.Bucket.BUFFER, TraceHealth.classify("ftrace_cpu_overrun_end"))
        assertEquals(TraceHealth.Bucket.META, TraceHealth.classify("mm_unknown_type"))
        assertEquals(TraceHealth.Bucket.META, TraceHealth.classify("power_rail_empty_packet"))
        assertEquals(TraceHealth.Bucket.PARSE, TraceHealth.classify("systrace_parse_failure"))
        assertEquals(TraceHealth.Bucket.PARSE, TraceHealth.classify("something_new_entirely"))
    }

    @Test
    fun `every verdict branch fires on the counters that define it`() {
        val clean = TraceHealth.evaluate("t", mapOf("canary" to 1L, "slices" to 10L, "sched_rows" to 5L))
        assertEquals(TraceHealth.Verdict.OK, clean.verdict)
        assertTrue(clean.windowsOk)
        assertTrue(clean.countsOk)

        val meta = TraceHealth.evaluate("t", mapOf("loss.mm_unknown_type" to 293L, "canary" to 1L))
        assertEquals(TraceHealth.Verdict.OK, meta.verdict)
        assertEquals(mapOf("mm_unknown_type" to 293L), meta.buckets.getValue(TraceHealth.Bucket.META))

        val parse = TraceHealth.evaluate("t", mapOf("loss.systrace_parse_failure" to 3L, "canary" to 1L))
        assertEquals(TraceHealth.Verdict.SLICES_INCOMPLETE, parse.verdict)
        assertTrue(parse.windowsOk)
        assertFalse(parse.countsOk)

        val lossy = TraceHealth.evaluate("t", mapOf("loss.traced_buf_chunks_overwritten" to 2L, "canary" to 1L))
        assertEquals(TraceHealth.Verdict.LOSSY, lossy.verdict)
        assertTrue(lossy.windowsOk)

        val unusable = TraceHealth.evaluate("t", mapOf("loss.traced_buf_chunks_overwritten" to 2L, "canary" to 0L))
        assertEquals(TraceHealth.Verdict.UNUSABLE, unusable.verdict)
        assertFalse(unusable.windowsOk)

        val missing = TraceHealth.evaluate("t", mapOf("canary" to 0L, "slices" to 10L))
        assertEquals(TraceHealth.Verdict.MISSING_CANARY, missing.verdict)
        assertFalse(missing.windowsOk)

        val summary = TraceHealth.summarize(listOf(clean, parse, lossy, unusable, missing))
        assertEquals(
            "trace health: 1/5 clean, 1 lossy, 1 missing-canary, 1 slices-incomplete, 1 unusable",
            summary.lines().first(),
        )
        assertTrue(summary.contains("PREVENTION (2/5"))
        assertTrue(summary.contains("CAVEAT (1/5"))
        assertTrue(summary.contains("INVESTIGATE (1/5"))
    }

    @Test
    fun `every frozen fixture trace reproduces the Python parse and verdict from the saved stdout`() {
        val goldens = TraceGoldens.all()
        assumeTrue("trace goldens not present", goldens.isNotEmpty())
        goldens.forEach { golden ->
            val parsed = golden.parsed()
            val rows = TraceHealth.parseRows(golden.csv("health"))
            val wantRows = parsed.getValue("health_rows_from_saved_stdout").jsonObject
                .mapValues { it.value.jsonPrimitive.long }
            assertEquals("${golden.stem} rows", wantRows, rows)

            val want = parsed.getValue("health_check_trace").jsonObject
            val got = TraceHealth.evaluate(golden.traceFileName, rows)
            assertEquals("${golden.stem} verdict", want.getValue("verdict").jsonPrimitive.content, got.verdict.key)
            assertEquals("${golden.stem} reason", want.getValue("reason").jsonPrimitive.content, got.reason)
            assertEquals("${golden.stem} canary", want.getValue("canary_slices").jsonPrimitive.long, got.canarySlices)
            assertEquals("${golden.stem} slices", want.getValue("slices").jsonPrimitive.long, got.slices)
            assertEquals("${golden.stem} sched", want.getValue("sched_rows").jsonPrimitive.long, got.schedRows)
            assertEquals("${golden.stem} windows_ok", want.getValue("windows_ok").jsonPrimitive.boolean, got.windowsOk)
            assertEquals("${golden.stem} counts_ok", want.getValue("counts_ok").jsonPrimitive.boolean, got.countsOk)
            val losses = want.getValue("losses").jsonObject.mapValues { it.value.jsonPrimitive.long }
            assertEquals("${golden.stem} losses", losses, got.losses)
            val buckets = want.getValue("buckets").jsonObject
            TraceHealth.Bucket.values().forEach { bucket ->
                val wantBucket = buckets.getValue(bucket.key).jsonObject.mapValues { it.value.jsonPrimitive.long }
                assertEquals("${golden.stem} ${bucket.key}", wantBucket, got.buckets.getValue(bucket))
            }
        }
    }
}
