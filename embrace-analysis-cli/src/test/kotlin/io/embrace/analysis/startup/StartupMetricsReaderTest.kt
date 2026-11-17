package io.embrace.analysis.startup

import androidx.benchmark.traceprocessor.Row
import androidx.benchmark.traceprocessor.rowOf
import io.embrace.analysis.benchmark.CaptureProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

internal class StartupMetricsReaderTest {

    private val reader = StartupMetricsReader()

    @Test
    fun `sections, window and TTID are read in ms, sections in the order the query returned them`() {
        val metrics = reader.fromRows(
            sections = sequenceOf(
                rowOf("name" to "emb-modules-init", "dur" to 7_261_963L),
                rowOf("name" to "emb-config-service-init", "dur" to 2_882_853L),
            ),
            window = rowOf("dur" to 14_411_743L, "src" to "emb-sdk-start", "wait" to 1_339_682L, "running" to 12_172_403L, "cpus" to 2L),
            ttid = rowOf("dur" to 247_247_030L),
            counters = emptySequence(),
            capture = capture(),
            config = rowOf("str_value" to "data_sources {\n  config {\n    ftrace_config {\n      atrace_apps: \"a\"\n    }\n  }\n}\n"),
        )

        assertEquals(listOf("emb-modules-init", "emb-config-service-init"), metrics.sections.keys.toList())
        assertEquals(CaptureProfile.APP_ONLY, metrics.capture)
        assertEquals(7.261963, metrics.sections.getValue("emb-modules-init"), 0.0)
        assertEquals(
            StartupWindow(
                durationMs = 14.411743,
                source = StartupWindow.Source.SDK_START,
                initThread = StartupWindow.ThreadScheduling(runningMs = 12.172403, waitMs = 1.339682, cpus = 2),
            ),
            metrics.window,
        )
        assertEquals(247.24703, metrics.ttidMs!!, 0.0)
        assertEquals(
            TraceHealth(
                verdict = TraceHealth.Verdict.OK,
                losses = emptyList(),
                importErrors = emptyList(),
                lossCountersReported = true,
                bufferWraps = 0,
                statsAfterData = true,
                scheduling = TraceHealth.SchedulingCoverage.FULL,
            ),
            metrics.health,
        )
    }

    @Test
    fun `a composed window is read as such`() {
        val metrics = read(window = window(src = "composed"))
        assertEquals(StartupWindow.Source.COMPOSED, metrics.window?.source)
    }

    @Test
    fun `a trace without a window or TTID has neither, rather than zeros`() {
        val metrics = reader.fromRows(
            sections = sequenceOf(rowOf("name" to "emb-modules-init", "dur" to 1_000_000L)),
            window = null,
            ttid = null,
            counters = emptySequence(),
            capture = capture(windowStart = null),
            config = null,
        )
        assertNull(metrics.window)
        assertNull(metrics.ttidMs)
        // and without a recorded config, its capture is unknown rather than guessed
        assertEquals(CaptureProfile.UNKNOWN, metrics.capture)
        assertEquals(mapOf("emb-modules-init" to 1.0), metrics.sections)
    }

    @Test
    fun `a window source the query doesn't produce is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            read(window = window(src = "guessed"))
        }
    }

    @Test
    fun `data loss makes a trace lossy, or unusable when the window went with it, and error counters don't`() {
        val loss = counter("traced_buf_data_loss_overwrite", idx = 1L, severity = "data_loss", value = 12L)
        val error = counter("power_rail_empty_packet", idx = null, severity = "error", value = 1L)

        val lossy = read(counters = sequenceOf(loss, error)).health
        assertEquals(TraceHealth.Verdict.LOSSY, lossy.verdict)
        assertEquals(listOf(TraceHealth.StatCounter("traced_buf_data_loss_overwrite", 1, 12)), lossy.losses)
        assertEquals(listOf(TraceHealth.StatCounter("power_rail_empty_packet", null, 1)), lossy.importErrors)

        assertEquals(TraceHealth.Verdict.UNUSABLE, read(window = null, counters = sequenceOf(loss)).health.verdict)
        assertEquals(TraceHealth.Verdict.OK, read(counters = sequenceOf(error)).health.verdict)
    }

    @Test
    fun `no window without a loss is a trace that never recorded one`() {
        assertEquals(TraceHealth.Verdict.NO_WINDOW, read(window = null).health.verdict)
    }

    @Test
    fun `a buffer wrap counts as loss only where the buffer stats can't say what a wrap lost`() {
        val reported = read(capture = capture(lossReported = 1L, bufferWraps = 3L)).health
        assertEquals(TraceHealth.Verdict.OK, reported.verdict)

        val unreported = read(capture = capture(lossReported = 0L, bufferWraps = 3L)).health
        assertEquals(TraceHealth.Verdict.LOSSY, unreported.verdict)
        assertFalse(unreported.lossCountersReported)
        assertEquals(3L, unreported.bufferWraps)

        assertEquals(TraceHealth.Verdict.OK, read(capture = capture(lossReported = 0L, bufferWraps = 0L)).health.verdict)
    }

    @Test
    fun `buffer stats recorded before any data was written are read as such, and leave the verdict to the window`() {
        val health = read(capture = capture(statsAfterData = 0L)).health
        assertFalse(health.statsAfterData)
        assertEquals(TraceHealth.Verdict.OK, health.verdict)
    }

    @Test
    fun `scheduling covers the window when its data starts at or before it, partially when after, not at all without rows`() {
        assertEquals(TraceHealth.SchedulingCoverage.FULL, coverage(capture(schedStart = 0L, windowStart = 100L)))
        assertEquals(TraceHealth.SchedulingCoverage.FULL, coverage(capture(schedStart = 100L, windowStart = 100L)))
        assertEquals(TraceHealth.SchedulingCoverage.PARTIAL, coverage(capture(schedStart = 101L, windowStart = 100L)))
        assertEquals(TraceHealth.SchedulingCoverage.NONE, coverage(capture(schedRows = 0L, schedStart = 0L)))
        assertEquals(TraceHealth.SchedulingCoverage.UNKNOWN, coverage(capture(schedStart = null)))
        assertEquals(TraceHealth.SchedulingCoverage.UNKNOWN, coverage(capture(windowStart = null)))
    }

    @Test
    fun `a counter severity the view doesn't produce is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            read(counters = sequenceOf(counter("x", idx = null, severity = "info", value = 1L)))
        }
    }

    private fun read(
        window: Row? = window(),
        counters: Sequence<Row> = emptySequence(),
        capture: Row = capture(),
    ): StartupMetrics = reader.fromRows(
        sections = emptySequence(),
        window = window,
        ttid = null,
        counters = counters,
        capture = capture,
        config = null,
    )

    private fun coverage(capture: Row) = read(capture = capture).health.scheduling

    private fun window(src: String = "emb-sdk-start") =
        rowOf("dur" to 1_000_000L, "src" to src, "wait" to 0L, "running" to 0L, "cpus" to 0L)

    private fun counter(name: String, idx: Long?, severity: String, value: Long) =
        rowOf("name" to name, "idx" to idx, "severity" to severity, "value" to value)

    private fun capture(
        schedRows: Long = 1_000L,
        schedStart: Long? = 0L,
        windowStart: Long? = 100L,
        lossReported: Long = 1L,
        bufferWraps: Long = 0L,
        statsAfterData: Long = 1L,
    ) = rowOf(
        "sched_rows" to schedRows,
        "sched_start" to schedStart,
        "window_start" to windowStart,
        "loss_reported" to lossReported,
        "buffer_wraps" to bufferWraps,
        "stats_after_data" to statsAfterData,
    )
}
