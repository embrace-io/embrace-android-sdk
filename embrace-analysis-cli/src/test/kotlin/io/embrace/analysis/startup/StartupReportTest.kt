package io.embrace.analysis.startup

import io.embrace.analysis.benchmark.IterationTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

internal class StartupReportTest {

    @Test
    fun `the window source is described when every trace has the same one`() {
        assertTrue(
            report(metrics(source = StartupWindow.Source.SDK_START)).contains("source: ${StartupWindow.Source.SDK_START.description}\n"),
        )
        assertTrue(
            report(metrics(source = StartupWindow.Source.COMPOSED)).contains("source: ${StartupWindow.Source.COMPOSED.description}\n"),
        )
    }

    @Test
    fun `traces with different window sources are called out, one without a window as a question mark`() {
        val text = report(metrics(source = StartupWindow.Source.SDK_START), metrics(windowMs = null))
        assertTrue(
            text.contains(
                "source: MIXED sources across traces: ['?', 'emb-sdk-start'] — do not compare iterations across sources\n",
            ),
        )
    }

    @Test
    fun `a canonical section reads its count, min, median, max and share of the median window`() {
        val text = report(
            metrics(sections = mapOf("emb-modules-init" to 8.0), windowMs = 10.0),
            metrics(sections = mapOf("emb-modules-init" to 9.0), windowMs = 12.0),
        )
        // median 8.5 of a median window of 11
        assertTrue(lines(text).contains("  ${"emb-modules-init".padEnd(60)}   2     8.00     8.50     9.00      77.3"))
    }

    @Test
    fun `with no window a section's share reads n-a and the window no data`() {
        val text = report(metrics(sections = mapOf("emb-modules-init" to 8.0), windowMs = null))
        assertTrue(lines(text).contains("  ${"emb-modules-init".padEnd(60)}   1     8.00     8.00     8.00       n/a"))
        assertTrue(lines(text).contains("  no data"))
    }

    @Test
    fun `a canonical section no trace has reads as not in these traces`() {
        val text = report(metrics())
        assertTrue(lines(text).contains("  ${"  ↳ emb-huc-init".padEnd(60)}${"-- not in these traces --".padStart(41)}"))
    }

    @Test
    fun `a section renamed since fills its current row from the old name, labelled with it`() {
        val text = report(metrics(sections = mapOf("emb-post-init" to 1.0)))
        assertTrue(lines(text).any { it.startsWith("  emb-setup-services (as post-init) ") })
        assertFalse(lines(text).any { it.startsWith("  emb-post-init ") })
    }

    @Test
    fun `a trace with the current name reads it, leaving the old name to the other sections`() {
        val text = report(metrics(sections = mapOf("emb-setup-services" to 2.0, "emb-post-init" to 1.0)))
        assertTrue(lines(text).any { it.startsWith("  emb-setup-services ") && !it.contains("(as ") })
        assertTrue(lines(text).any { it.startsWith("  emb-post-init ") })
    }

    @Test
    fun `the other sections are the 15 largest medians, or every one with all sections`() {
        val sections = (1..16).associate { "emb-extra-$it" to it.toDouble() } + ("emb-sdk-start" to 100.0)
        val top = report(metrics(sections = sections))
        assertTrue(top.contains("other emb-* sections (top 15 by median; not part of the canonical breakdown):\n"))
        assertTrue(lines(top).any { it.startsWith("  emb-extra-2 ") })
        assertFalse(lines(top).any { it.startsWith("  emb-extra-1 ") })
        // the window's own slice is never listed as a section
        assertFalse(lines(top).any { it.startsWith("  emb-sdk-start ") })

        val all = StartupReport(listOf(iteration(0, metrics(sections = sections))), allSections = true).render()
        assertTrue(all.contains("other emb-* sections (all; not part of the canonical breakdown):\n"))
        assertTrue(lines(all).any { it.startsWith("  emb-extra-1 ") })
    }

    @Test
    fun `other sections with equal medians keep the order the traces returned them in`() {
        val text = report(metrics(sections = linkedMapOf("emb-b" to 1.0, "emb-a" to 1.0, "emb-c" to 2.0)))
        val order = lines(text).map { it.trim().substringBefore(' ') }.filter { it in setOf("emb-a", "emb-b", "emb-c") }
        assertEquals(listOf("emb-c", "emb-b", "emb-a"), order)
    }

    @Test
    fun `an iteration whose wait is over 15 percent of the window is flagged contended, one at 15 percent is not`() {
        val text = report(
            metrics(windowMs = 10.0, waitMs = 2.0),
            metrics(windowMs = 10.0, waitMs = 1.5),
            metrics(windowMs = null),
        )
        assertTrue(lines(text).contains("  ${"iter000_x".padEnd(52)}    10.0     9.0     2.0  20.0%     2  CONTENDED"))
        assertTrue(lines(text).contains("  ${"iter001_x".padEnd(52)}    10.0     9.0     1.5  15.0%     2"))
        // a trace without a window has no scheduling row
        assertFalse(lines(text).any { it.contains("iter002_x") })
        assertTrue(lines(text).contains("  1/3 iterations contended (wait > 15% of window)."))
    }

    private fun report(vararg metrics: StartupMetrics): String =
        StartupReport(metrics.mapIndexed(::iteration), allSections = false).render()

    private fun iteration(index: Int, metrics: StartupMetrics) = AnalyzedIteration(
        trace = IterationTrace(
            path = Paths.get("StartupBenchmarks_coldStartup_iter00${index}_x.perfetto-trace"),
            index = index,
            shortName = "iter00${index}_x",
        ),
        metrics = metrics,
    )

    private fun lines(text: String): List<String> = text.split('\n')

    private fun metrics(
        sections: Map<String, Double> = emptyMap(),
        windowMs: Double? = 10.0,
        source: StartupWindow.Source = StartupWindow.Source.SDK_START,
        waitMs: Double = 0.5,
    ) = StartupMetrics(
        sections = sections,
        window = windowMs?.let {
            StartupWindow(
                durationMs = it,
                source = source,
                initThread = StartupWindow.ThreadScheduling(runningMs = 9.0, waitMs = waitMs, cpus = 2),
            )
        },
        ttidMs = 300.0,
    )
}
