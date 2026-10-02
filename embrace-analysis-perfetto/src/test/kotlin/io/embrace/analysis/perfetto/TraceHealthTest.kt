package io.embrace.analysis.perfetto

import io.embrace.analysis.fixtures.TraceGoldens
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Path

/**
 * Trace-health verdicts: the bucket table and every verdict branch on synthetic counters, then the
 * golden's own verdict on every frozen fixture trace (`python_parsed.json > health_check_trace`)
 * reproduced from the saved health-query stdout, and the per-trace and summary lines the command
 * prints, against the frozen CLI output.
 */
class TraceHealthTest {

    /**
     * A profile with the values the frozen fixtures were captured under. These are test data here, not a
     * dependency on any startup module: the checker is generic and the fixtures happen to be SDK traces.
     */
    private val profile = TraceHealth.Profile(
        canary = "emb-sdk-start",
        burstSection = "emb-start-first-session",
        burstThreshold = 80L,
        burstAdvice = "a serializer is being resolved at runtime on the new-user-session write (the kotlinx " +
            "builtin serializer table, several times more expensive on an uncompiled install). Every SDK call " +
            "must pass a static SerializationStrategy; look for a reified serializer<T>() that crept back in.",
    )

    @Test
    fun `per-trace and summary lines match the golden CLI output on the fixture traces`() {
        val traces = TraceGoldens.all()
        assumeTrue("trace goldens not present", traces.isNotEmpty())
        traces.groupBy { it.device }.forEach { (device, goldensForDevice) ->
            val want = TraceGoldens.cliStdout("trace_health.$device.stdout.txt") ?: return@forEach
            val reports = goldensForDevice.sortedBy { it.traceFileName }.map { g ->
                TraceHealth.evaluate(g.traceFileName, TraceHealth.parseRows(g.csv("health")))
            }
            val lines = reports.filter { it.verdict != TraceHealth.Verdict.OK }
                .map { TraceHealth.perTraceLine(it, Path.of(it.trace), showMeta = false) } +
                TraceHealth.summarize(reports, profile).lines()
            assertEquals("$device trace-health", want.trimEnd(), lines.joinToString("\n").trimEnd())
        }
    }

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

        val summary = TraceHealth.summarize(listOf(clean, parse, lossy, unusable, missing), profile)
        assertEquals(
            "trace health: 1/5 clean, 1 lossy, 1 missing-canary, 1 slices-incomplete, 1 unusable",
            summary.lines().first(),
        )
        assertTrue(summary.contains("PREVENTION (2/5"))
        assertTrue(summary.contains("CAVEAT (1/5"))
        assertTrue(summary.contains("INVESTIGATE (1/5"))
        assertFalse(summary.contains("REGRESSION"))

        // the runtime-serializer signature: a class-load burst inside start-first-session, verdict untouched
        val threshold = profile.burstThreshold
        val burst = TraceHealth.evaluate("t", mapOf("canary" to 1L), classLoadsInBurstSection = 119L, burstThreshold = threshold)
        assertEquals(TraceHealth.Verdict.OK, burst.verdict)
        assertTrue(burst.classLoadBurst)
        val restored = TraceHealth.evaluate("t", mapOf("canary" to 1L), classLoadsInBurstSection = 1L, burstThreshold = threshold)
        assertFalse(restored.classLoadBurst)
        // a healthy create-path launch loads up to about fifty classes there and must NOT be flagged
        val created = TraceHealth.evaluate("t", mapOf("canary" to 1L), classLoadsInBurstSection = 50L, burstThreshold = threshold)
        assertFalse(created.classLoadBurst)
        assertFalse(clean.classLoadBurst) // not measured => not flagged
        // measured but no threshold given => the checker cannot judge, so it does not flag
        assertFalse(TraceHealth.evaluate("t", mapOf("canary" to 1L), classLoadsInBurstSection = 119L).classLoadBurst)
        val burstSummary = TraceHealth.summarize(listOf(burst, restored, created, clean), profile)
        assertEquals("trace health: 4/4 clean", burstSummary.lines().first())
        assertTrue(burstSummary.contains("REGRESSION (1/4 loaded >= 80 classes inside emb-start-first-session"))
    }

    @Test
    fun `class-load query parses through the same row reader`() {
        assertEquals(mapOf("classloads" to 55L), TraceHealth.parseRows("\"k\",\"v\"\n\"classloads\",55\n"))
        assertTrue(TraceHealth.classLoadSql(profile.burstSection).contains("s2.name = 'emb-start-first-session'"))
        assertTrue(TraceHealth.classLoadSql("emb-post-init").contains("s2.name = 'emb-post-init'"))
    }

    @Test
    fun `every frozen fixture trace reproduces the golden parse and verdict from the saved stdout`() {
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
