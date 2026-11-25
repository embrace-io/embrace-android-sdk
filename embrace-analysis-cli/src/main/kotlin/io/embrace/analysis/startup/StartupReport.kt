package io.embrace.analysis.startup

import io.embrace.analysis.stats.Distribution
import java.util.Locale

/**
 * The startup report of a set of [iterations], in iteration order: each trace's health, the SDK-init window, TTID, each
 * canonical section's duration and share of the window, the other `emb-*` sections, and each iteration's init-thread
 * scheduling inside the window. [allSections] lists every other section instead of the [TOP_EXTRAS] with the largest
 * medians. A trace whose health is [TraceHealth.Verdict.UNUSABLE] is left out of every figure after its health.
 */
class StartupReport(
    private val iterations: List<AnalyzedIteration>,
    private val allSections: Boolean,
) {

    private val usable: List<AnalyzedIteration> =
        iterations.filter { it.metrics.health.verdict != TraceHealth.Verdict.UNUSABLE }

    private val windows: List<StartupWindow> = usable.mapNotNull { it.metrics.window }

    /** The report text, every line ending in a newline. */
    fun render(): String = buildString {
        line("traces analyzed: ${iterations.size}")
        line()
        healthBlock()
        windowBlock()
        ttidBlock()
        val sectionValues = sectionValues()
        canonicalBlock(sectionValues)
        sectionValues.remove(StartupWindow.Source.SDK_START.key) // reported as the window, not a section
        otherSectionsBlock(sectionValues)
        schedulingBlock()
    }

    private fun StringBuilder.healthBlock() {
        val healths = iterations.map { it.metrics.health }
        val counts = healths.groupingBy { it.verdict }.eachCount()
        val notOk = TraceHealth.Verdict.entries.filter { it != TraceHealth.Verdict.OK && it in counts }
        line(
            "trace health: ${counts[TraceHealth.Verdict.OK] ?: 0}/${iterations.size} ${TraceHealth.Verdict.OK.key}" +
                notOk.joinToString("") { ", ${counts.getValue(it)} ${it.key}" },
        )
        iterations.filter { it.metrics.health.verdict != TraceHealth.Verdict.OK }.forEach { iteration ->
            val health = iteration.metrics.health
            row(HEALTH_ROW, iteration.trace.shortName, health.verdict.key, lossDetail(health))
        }
        notOk.forEach { line("  ${it.key} (${counts.getValue(it)}): ${it.guidance}") }
        val unseen = healths.count { !it.statsAfterData }
        if (unseen > 0) {
            line(
                "  $unseen/${iterations.size} traces have buffer stats recorded before any data was written, which can't " +
                    "show a loss: judged on their window alone.",
            )
        }
        val importErrors = healths.flatMap { health -> health.importErrors.map { it.name }.distinct() }
            .groupingBy { it }.eachCount().toSortedMap()
        if (importErrors.isNotEmpty()) {
            line(
                "  import errors (what trace_processor couldn't read; durations stand): " +
                    importErrors.entries.joinToString(", ") { (name, traces) -> "$name in $traces" },
            )
        }
        line()
    }

    /** What a trace lost: its data-loss counters, or the buffer wraps that count as loss where those aren't reported. */
    private fun lossDetail(health: TraceHealth): String {
        val counters = health.losses.map { counter ->
            val buffer = counter.buffer?.let { "[$it]" }.orEmpty()
            "${counter.name}$buffer=${counter.value}"
        }
        val wraps = if (!health.lossCountersReported && health.bufferWraps > 0) {
            listOf("traced_buf_chunks_overwritten=${health.bufferWraps} (loss not reported)")
        } else {
            emptyList()
        }
        return (counters + wraps).joinToString(", ").ifEmpty { "nothing lost" }
    }

    private fun StringBuilder.windowBlock() {
        val durations = windows.map { it.durationMs }
        line("SDK-init window, ms — source: ${windowSourceDescription()}")
        line("  ${Distribution.summaryOf(durations)}")
        line("  per-iteration: ${durations.joinToString(", ") { "%.1f".format(Locale.ROOT, it) }}")
        line()
    }

    private fun windowSourceDescription(): String {
        val sources = usable.map { it.metrics.window?.source }.toSet()
        val only = sources.singleOrNull()
        return if (only != null) {
            only.description
        } else {
            val keys = sources.map { it?.key ?: UNKNOWN_SOURCE }.sorted()
            "MIXED sources across traces: ${keys.joinToString(", ", "[", "]") { "'$it'" }} — " +
                "do not compare iterations across sources"
        }
    }

    private fun StringBuilder.ttidBlock() {
        line("TTID (android.startup module; anchor differs a few ms from macrobenchmark JSON), ms:")
        line("  ${Distribution.summaryOf(usable.mapNotNull { it.metrics.ttidMs })}")
        line()
    }

    private fun StringBuilder.canonicalBlock(sectionValues: MutableMap<String, List<Double>>) {
        val windowMedian = windows.takeIf { it.isNotEmpty() }?.let { Distribution(it.map(StartupWindow::durationMs)).median }
        line("canonical sections, execution order (first-occurrence slice durations, ms):")
        row(SECTION_HEADER, "section", "n", "min", "median", "max", "% of win")
        CANONICAL_SECTIONS.forEach { section ->
            var label = canonicalLabel(section)
            var values = sectionValues.remove(section.name).orEmpty()
            if (values.isEmpty()) {
                // older SDKs: see OLDER_SECTION_NAMES
                sectionValues.removeUnderOlderName(section.name)?.let { (oldName, oldValues) ->
                    values = oldValues
                    label += " (as ${oldName.removePrefix(SECTION_PREFIX)})"
                }
            }
            if (values.isEmpty()) {
                row(MISSING_ROW, label, "-- not in these traces --")
            } else {
                val distribution = Distribution(values)
                val share = if (windowMedian != null && windowMedian != 0.0) {
                    "%.1f".format(Locale.ROOT, distribution.median / windowMedian * PERCENT)
                } else {
                    "n/a"
                }
                row(SECTION_ROW, label, distribution.count, distribution.min, distribution.median, distribution.max, share)
            }
        }
        line()
    }

    private fun StringBuilder.otherSectionsBlock(sectionValues: Map<String, List<Double>>) {
        // largest median first; equal medians keep the order the traces returned the sections in
        val ranked = sectionValues.entries.map { (name, values) -> name to Distribution(values) }
            .sortedByDescending { (_, distribution) -> distribution.median }
        val shown = if (allSections) {
            ranked
        } else {
            ranked.take(TOP_EXTRAS)
        }
        if (shown.isEmpty()) {
            return
        }
        val which = if (allSections) {
            "all"
        } else {
            "top $TOP_EXTRAS by median"
        }
        line("other emb-* sections ($which; not part of the canonical breakdown):")
        shown.forEach { (name, d) -> row(OTHER_SECTION_ROW, name, d.count, d.min, d.median, d.max) }
        line()
    }

    private fun StringBuilder.schedulingBlock() {
        line("main-thread scheduling inside the window (contention / slow-execution check):")
        val withWindow = usable.filter { it.metrics.window != null }
        if (withWindow.isNotEmpty() && withWindow.all { it.metrics.health.scheduling == TraceHealth.SchedulingCoverage.NONE }) {
            line("  none: these traces hold no scheduling data, as the capture records none.")
            return
        }
        row(SCHEDULING_HEADER, "iteration", "window", "run", "wait", "wait%", "cpus")
        var contended = 0
        var judged = 0
        withWindow.forEach { iteration ->
            val window = checkNotNull(iteration.metrics.window)
            val coverage = iteration.metrics.health.scheduling
            if (coverage == TraceHealth.SchedulingCoverage.NONE) {
                row(SCHEDULING_MISSING_ROW, iteration.trace.shortName, window.durationMs, "no scheduling data")
                return@forEach
            }
            val thread = window.initThread
            val share = thread.waitMs / window.durationMs
            val flag = when {
                coverage == TraceHealth.SchedulingCoverage.PARTIAL -> "  PARTIAL: scheduling starts inside the window"
                share > CONTENTION_THRESHOLD -> "  CONTENDED"
                else -> ""
            }
            if (coverage != TraceHealth.SchedulingCoverage.PARTIAL) {
                judged++
                if (share > CONTENTION_THRESHOLD) {
                    contended++
                }
            }
            row(
                SCHEDULING_ROW,
                iteration.trace.shortName,
                window.durationMs,
                thread.runningMs,
                thread.waitMs,
                share * PERCENT,
                thread.cpus,
                flag,
            )
        }
        val notJudged = iterations.size - judged
        line(
            "  $contended/$judged iterations contended " +
                "(wait > ${"%.0f".format(Locale.ROOT, CONTENTION_THRESHOLD * PERCENT)}% of window)" +
                if (notJudged > 0) {
                    "; $notJudged not judged (unusable, no window, or scheduling that doesn't cover the window)."
                } else {
                    "."
                },
        )
        val unknown = withWindow.count { it.metrics.health.scheduling == TraceHealth.SchedulingCoverage.UNKNOWN }
        if (unknown > 0) {
            line(
                "  $unknown judged as though their scheduling covered the window: their tracing service doesn't report " +
                    "where it starts.",
            )
        }
        line("  Two slow signatures (see references/sections.md): high wait% = scheduler contention;")
        line("  low wait% but elevated run time vs a fast pass = slower execution (core placement /")
        line("  clocks). Judge regressions on iterations that show neither.")
    }

    /** Each section's values across the iterations, sections in the order the iterations first returned them. */
    private fun sectionValues(): MutableMap<String, List<Double>> {
        val values = LinkedHashMap<String, MutableList<Double>>()
        usable.forEach { iteration ->
            iteration.metrics.sections.forEach { (name, duration) -> values.getOrPut(name) { ArrayList() }.add(duration) }
        }
        return LinkedHashMap<String, List<Double>>(values)
    }

    private fun StringBuilder.row(format: String, vararg columns: Any) {
        line(format.format(Locale.ROOT, *columns))
    }

    private fun StringBuilder.line(text: String = "") {
        append(text).append('\n')
    }

    /** A canonical section's name indented by its nesting depth, a nested one marked `↳`. */
    private fun canonicalLabel(
        section: CanonicalSection,
    ): String = "  ".repeat(section.depth) + if (section.depth > 0) {
        "↳ ${section.name}"
    } else {
        section.name
    }

    companion object {

        /** Iterations whose init-thread wait exceeds this share of the window are flagged CONTENDED. */
        const val CONTENTION_THRESHOLD = 0.15

        /** How many of the other sections are listed without [allSections]. */
        const val TOP_EXTRAS = 15

        // The report's rows: a 60-wide section label then count, min, median, max and share of the window; a 52-wide
        // iteration name then its window, running, wait, wait share and CPUs, or its window and why there are no
        // scheduling figures; a 52-wide iteration name then its health verdict and what it lost.
        private const val HEALTH_ROW = "  %-52s%-11s%s"
        private const val SCHEDULING_MISSING_ROW = "  %-52s%8.1f  %s"
        private const val SECTION_HEADER = "  %-60s%4s%9s%9s%9s%10s"
        private const val SECTION_ROW = "  %-60s%4d%9.2f%9.2f%9.2f%10s"
        private const val MISSING_ROW = "  %-60s%41s"
        private const val OTHER_SECTION_ROW = "  %-60s%4d%9.2f%9.2f%9.2f"
        private const val SCHEDULING_HEADER = "  %-52s%8s%8s%8s%7s%6s"
        private const val SCHEDULING_ROW = "  %-52s%8.1f%8.1f%8.1f%6.1f%%%6d%s"

        private const val UNKNOWN_SOURCE = "?"
        private const val SECTION_PREFIX = "emb-"
        private const val PERCENT = 100

        /**
         * The values of the section [name] was called before a rename ([OLDER_SECTION_NAMES]), removed from this map
         * with that name: an older SDK's section fills the current one's row, labelled with its old name.
         */
        private fun MutableMap<String, List<Double>>.removeUnderOlderName(name: String): Pair<String, List<Double>>? {
            val oldName = OLDER_SECTION_NAMES[name] ?: return null
            return remove(oldName)?.let { oldName to it }
        }
    }
}
