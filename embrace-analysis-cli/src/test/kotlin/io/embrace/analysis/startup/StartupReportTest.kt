package io.embrace.analysis.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class StartupReportTest {

    @Test
    fun `the window source is described when every trace has the same one`() {
        assertTrue(
            report(
                startupMetrics(source = StartupWindow.Source.SDK_START),
            ).contains("source: ${StartupWindow.Source.SDK_START.description}\n"),
        )
        assertTrue(
            report(
                startupMetrics(source = StartupWindow.Source.COMPOSED),
            ).contains("source: ${StartupWindow.Source.COMPOSED.description}\n"),
        )
    }

    @Test
    fun `traces with different window sources are called out, one without a window as a question mark`() {
        val text = report(startupMetrics(source = StartupWindow.Source.SDK_START), startupMetrics(windowMs = null))
        assertTrue(
            text.contains(
                "source: MIXED sources across traces: ['?', 'emb-sdk-start'] — do not compare iterations across sources\n",
            ),
        )
    }

    @Test
    fun `a canonical section reads its count, min, median, max and share of the median window`() {
        val text = report(
            startupMetrics(sections = mapOf("emb-modules-init" to 8.0), windowMs = 10.0),
            startupMetrics(sections = mapOf("emb-modules-init" to 9.0), windowMs = 12.0),
        )
        // median 8.5 of a median window of 11
        assertTrue(lines(text).contains("  ${"emb-modules-init".padEnd(60)}   2     8.00     8.50     9.00      77.3"))
    }

    @Test
    fun `with no window a section's share reads n-a and the window no data`() {
        val text = report(startupMetrics(sections = mapOf("emb-modules-init" to 8.0), windowMs = null))
        assertTrue(lines(text).contains("  ${"emb-modules-init".padEnd(60)}   1     8.00     8.00     8.00       n/a"))
        assertTrue(lines(text).contains("  no data"))
    }

    @Test
    fun `a canonical section no trace has reads as not in these traces`() {
        val text = report(startupMetrics())
        assertTrue(lines(text).contains("  ${"  ↳ emb-huc-init".padEnd(60)}${"-- not in these traces --".padStart(41)}"))
    }

    @Test
    fun `a section renamed since fills its current row from the old name, labelled with it`() {
        val text = report(startupMetrics(sections = mapOf("emb-post-init" to 1.0)))
        assertTrue(lines(text).any { it.startsWith("  emb-setup-services (as post-init) ") })
        assertFalse(lines(text).any { it.startsWith("  emb-post-init ") })
    }

    @Test
    fun `a trace with the current name reads it, leaving the old name to the other sections`() {
        val text = report(startupMetrics(sections = mapOf("emb-setup-services" to 2.0, "emb-post-init" to 1.0)))
        assertTrue(lines(text).any { it.startsWith("  emb-setup-services ") && !it.contains("(as ") })
        assertTrue(lines(text).any { it.startsWith("  emb-post-init ") })
    }

    @Test
    fun `the other sections are the 15 largest medians, or every one with all sections`() {
        val sections = (1..16).associate { "emb-extra-$it" to it.toDouble() } + ("emb-sdk-start" to 100.0)
        val top = report(startupMetrics(sections = sections))
        assertTrue(top.contains("other emb-* sections (top 15 by median; not part of the canonical breakdown):\n"))
        assertTrue(lines(top).any { it.startsWith("  emb-extra-2 ") })
        assertFalse(lines(top).any { it.startsWith("  emb-extra-1 ") })
        // the window's own slice is never listed as a section
        assertFalse(lines(top).any { it.startsWith("  emb-sdk-start ") })

        val all = StartupReport(listOf(analyzedIteration(0, startupMetrics(sections = sections))), allSections = true).render()
        assertTrue(all.contains("other emb-* sections (all; not part of the canonical breakdown):\n"))
        assertTrue(lines(all).any { it.startsWith("  emb-extra-1 ") })
    }

    @Test
    fun `other sections with equal medians keep the order the traces returned them in`() {
        val text = report(startupMetrics(sections = linkedMapOf("emb-b" to 1.0, "emb-a" to 1.0, "emb-c" to 2.0)))
        val order = lines(text).map { it.trim().substringBefore(' ') }.filter { it in setOf("emb-a", "emb-b", "emb-c") }
        assertEquals(listOf("emb-c", "emb-b", "emb-a"), order)
    }

    @Test
    fun `an iteration whose wait is over 15 percent of the window is flagged contended, one at 15 percent is not`() {
        val text = report(
            startupMetrics(windowMs = 10.0, waitMs = 2.0),
            startupMetrics(windowMs = 10.0, waitMs = 1.5),
            startupMetrics(windowMs = null),
        )
        assertTrue(lines(text).contains("  ${"iter000_x".padEnd(52)}    10.0     9.0     2.0  20.0%     2  CONTENDED"))
        assertTrue(lines(text).contains("  ${"iter001_x".padEnd(52)}    10.0     9.0     1.5  15.0%     2"))
        // a trace without a window has no scheduling row, and isn't judged
        assertFalse(lines(text).any { it.contains("iter002_x") })
        assertTrue(
            lines(text).contains(
                "  1/2 iterations contended (wait > 15% of window); " +
                    "1 not judged (unusable, no window, or scheduling that doesn't cover the window).",
            ),
        )
    }

    @Test
    fun `every trace's health is counted, and each that isn't ok is listed with what it lost and the guidance once`() {
        val lossy = traceHealth(
            verdict = TraceHealth.Verdict.LOSSY,
            losses = listOf(TraceHealth.StatCounter("traced_buf_data_loss_overwrite", 1, 12)),
        )
        val text = report(startupMetrics(), startupMetrics(health = lossy), startupMetrics(health = lossy))
        assertTrue(lines(text).contains("trace health: 1/3 ok, 2 lossy"))
        assertTrue(lines(text).contains("  ${"iter001_x".padEnd(52)}${"lossy".padEnd(11)}traced_buf_data_loss_overwrite[1]=12"))
        assertEquals(1, lines(text).count { it == "  lossy (2): ${TraceHealth.Verdict.LOSSY.guidance}" })
    }

    @Test
    fun `a wrap counted as loss is named as such, and traces whose stats predate their data are counted`() {
        val text = report(
            startupMetrics(health = traceHealth(verdict = TraceHealth.Verdict.LOSSY, lossCountersReported = false, bufferWraps = 3)),
            startupMetrics(health = traceHealth(lossCountersReported = false)),
            startupMetrics(health = traceHealth(statsAfterData = false)),
        )
        assertTrue(lines(text).any { it.endsWith("traced_buf_chunks_overwritten=3 (loss not reported)") })
        // a trace whose stats don't count overwritten data, but that never wrapped, lost nothing and isn't listed
        assertTrue(lines(text).contains("trace health: 2/3 ok, 1 lossy"))
        assertTrue(text.contains("  1/3 traces have buffer stats recorded before any data was written, which can't show a loss"))
    }

    @Test
    fun `import errors are listed with how many traces had each, and leave the verdict alone`() {
        val error = TraceHealth.StatCounter("power_rail_empty_packet", null, 1)
        val withError = startupMetrics(health = traceHealth(importErrors = listOf(error)))
        val text = report(withError, withError)
        assertTrue(lines(text).contains("trace health: 2/2 ok"))
        assertTrue(
            lines(text).contains("  import errors (what trace_processor couldn't read; durations stand): power_rail_empty_packet in 2"),
        )
    }

    @Test
    fun `an unusable trace is left out of every figure after its health`() {
        val text = report(
            startupMetrics(sections = mapOf("emb-modules-init" to 8.0), windowMs = 10.0),
            startupMetrics(
                sections = mapOf("emb-modules-init" to 99.0),
                windowMs = null,
                health = traceHealth(verdict = TraceHealth.Verdict.UNUSABLE),
            ),
        )
        assertTrue(lines(text).contains("trace health: 1/2 ok, 1 unusable"))
        assertTrue(lines(text).contains("  ${"emb-modules-init".padEnd(60)}   1     8.00     8.00     8.00      80.0"))
        assertTrue(lines(text).contains("  n=1  min=300.0  median=300.0  mean=300.0  max=300.0"))
    }

    @Test
    fun `traces with no scheduling data say so instead of reading as zero`() {
        val none = traceHealth(scheduling = TraceHealth.SchedulingCoverage.NONE)
        val all = report(startupMetrics(health = none), startupMetrics(health = none))
        assertTrue(lines(all).contains("  none: these traces hold no scheduling data, as the capture records none."))
        assertFalse(all.contains("iterations contended"))

        val some = report(startupMetrics(), startupMetrics(health = none))
        assertTrue(lines(some).contains("  ${"iter001_x".padEnd(52)}    10.0  no scheduling data"))
        assertTrue(lines(some).any { it.startsWith("  0/1 iterations contended") })
    }

    @Test
    fun `scheduling that starts inside the window is flagged and not judged, and an unknown start is judged but noted`() {
        val text = report(
            startupMetrics(waitMs = 5.0, health = traceHealth(scheduling = TraceHealth.SchedulingCoverage.PARTIAL)),
            startupMetrics(health = traceHealth(scheduling = TraceHealth.SchedulingCoverage.UNKNOWN)),
        )
        val partial = lines(text).single { it.startsWith("  iter000_x") }
        assertTrue(partial, partial.endsWith("  PARTIAL: scheduling starts inside the window"))
        assertTrue(lines(text).any { it.startsWith("  0/1 iterations contended") && it.contains("1 not judged") })
        assertTrue(lines(text).any { it.startsWith("  1 judged as though their scheduling covered the window") })
    }

    private fun report(vararg metrics: StartupMetrics): String =
        StartupReport(metrics.mapIndexed(::analyzedIteration), allSections = false).render()

    private fun lines(text: String): List<String> = text.split('\n')
}
