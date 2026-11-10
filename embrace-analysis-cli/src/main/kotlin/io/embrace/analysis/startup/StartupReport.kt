package io.embrace.analysis.startup

import io.embrace.analysis.stats.Distribution
import java.util.Locale

/**
 * The startup report of a set of [iterations], in iteration order: the SDK-init window, TTID, each canonical section's
 * duration and share of the window, the other `emb-*` sections, and each iteration's init-thread scheduling inside the
 * window. [allSections] lists every other section instead of the [TOP_EXTRAS] with the largest medians.
 */
class StartupReport(
    private val iterations: List<AnalyzedIteration>,
    private val allSections: Boolean,
) {

    private val windows: List<StartupWindow> = iterations.mapNotNull { it.metrics.window }

    /** The report text, every line ending in a newline. */
    fun render(): String = buildString {
        line("traces analyzed: ${iterations.size}")
        line()
        windowBlock()
        ttidBlock()
        val sectionValues = sectionValues()
        canonicalBlock(sectionValues)
        sectionValues.remove(StartupWindow.Source.SDK_START.key) // reported as the window, not a section
        otherSectionsBlock(sectionValues)
        schedulingBlock()
    }

    private fun StringBuilder.windowBlock() {
        val durations = windows.map { it.durationMs }
        line("SDK-init window, ms — source: ${windowSourceDescription()}")
        line("  ${Distribution.summaryOf(durations)}")
        line("  per-iteration: ${durations.joinToString(", ") { "%.1f".format(Locale.ROOT, it) }}")
        line()
    }

    private fun windowSourceDescription(): String {
        val sources = iterations.map { it.metrics.window?.source }.toSet()
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
        line("  ${Distribution.summaryOf(iterations.mapNotNull { it.metrics.ttidMs })}")
        line()
    }

    private fun StringBuilder.canonicalBlock(sectionValues: MutableMap<String, List<Double>>) {
        val windowMedian = windows.takeIf { it.isNotEmpty() }?.let { Distribution(it.map(StartupWindow::durationMs)).median }
        line("canonical sections, execution order (first-occurrence slice durations, ms):")
        row(SECTION_HEADER, "section", "n", "min", "median", "max", "% of win")
        CANONICAL_SECTIONS.forEach { section ->
            var label = section.label
            var values = sectionValues.remove(section.name).orEmpty()
            if (values.isEmpty()) {
                // older SDKs: see the end of the companion
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
        row(SCHEDULING_HEADER, "iteration", "window", "run", "wait", "wait%", "cpus")
        var contended = 0
        iterations.forEach { iteration ->
            val window = iteration.metrics.window ?: return@forEach
            val thread = window.initThread
            val share = thread.waitMs / window.durationMs
            val flag = if (share > CONTENTION_THRESHOLD) {
                contended++
                "  CONTENDED"
            } else {
                ""
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
        line(
            "  $contended/${iterations.size} iterations contended " +
                "(wait > ${"%.0f".format(Locale.ROOT, CONTENTION_THRESHOLD * PERCENT)}% of window).",
        )
        line("  Two slow signatures (see references/sections.md): high wait% = scheduler contention;")
        line("  low wait% but elevated run time vs a fast pass = slower execution (core placement /")
        line("  clocks). Judge regressions on iterations that show neither.")
    }

    /** Each section's values across the iterations, sections in the order the iterations first returned them. */
    private fun sectionValues(): MutableMap<String, List<Double>> {
        val values = LinkedHashMap<String, MutableList<Double>>()
        iterations.forEach { iteration ->
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

    /** A canonical section and its nesting depth among them, which indents its [label]. */
    private class CanonicalSection(val name: String, depth: Int) {
        val label: String = "  ".repeat(depth) + if (depth > 0) {
            "↳ $name"
        } else {
            name
        }
    }

    companion object {

        /** Iterations whose init-thread wait exceeds this share of the window are flagged CONTENDED. */
        const val CONTENTION_THRESHOLD = 0.15

        /** How many of the other sections are listed without [allSections]. */
        const val TOP_EXTRAS = 15

        /**
         * The canonical init sections in execution order (see references/sections.md), with their nesting depth among
         * them. These are the sections exported as `<name>-duration-ms` attributes on the sdk-init span, except the
         * two SharedPreferences sections, which nest wherever the store is first touched and are listed with the others.
         */
        private val CANONICAL_SECTIONS = listOf(
            CanonicalSection("emb-embrace-impl-init", 0),
            CanonicalSection("emb-modules-init", 0),
            CanonicalSection("emb-persisted-config-init", 1),
            CanonicalSection("emb-config-service-init", 1),
            CanonicalSection("emb-startup-classifier-init", 1),
            CanonicalSection("emb-process-state-tracker-init", 1),
            CanonicalSection("emb-otel-tracer-init", 1),
            CanonicalSection("emb-instrumentation-args-init", 1),
            CanonicalSection("emb-payload-storage-service-init", 1),
            CanonicalSection("emb-resource-source-init", 1),
            CanonicalSection("emb-session-orchestrator-init", 1),
            CanonicalSection("emb-current-session-part-span-initialize-service", 1),
            CanonicalSection("emb-process-state-tracker-register", 0),
            CanonicalSection("emb-setup-services", 0),
            CanonicalSection("emb-start-first-session", 1),
            CanonicalSection("emb-post-services-setup", 0),
            CanonicalSection("emb-load-instrumentation", 1),
            CanonicalSection("emb-huc-init", 1),
        )

        // The report's rows: a 60-wide section label then count, min, median, max and share of the window; a 52-wide
        // iteration name then its window, running, wait, wait share and CPUs.
        private const val SECTION_HEADER = "  %-60s%4s%9s%9s%9s%10s"
        private const val SECTION_ROW = "  %-60s%4d%9.2f%9.2f%9.2f%10s"
        private const val MISSING_ROW = "  %-60s%41s"
        private const val OTHER_SECTION_ROW = "  %-60s%4d%9.2f%9.2f%9.2f"
        private const val SCHEDULING_HEADER = "  %-52s%8s%8s%8s%7s%6s"
        private const val SCHEDULING_ROW = "  %-52s%8.1f%8.1f%8.1f%6.1f%%%6d%s"

        private const val UNKNOWN_SOURCE = "?"
        private const val SECTION_PREFIX = "emb-"
        private const val PERCENT = 100

        /*
         * Older SDKs (.claude/skills/startup-analysis/references/section-history.md): a canonical section renamed since
         * an older SDK fills its row from its old name, labelled with it.
         */

        /**
         * The names canonical sections had before a rename that left their work unchanged, as current name -> older
         * name. Sections that were split, merged or re-scoped have no entry and are not comparable across the change;
         * references/section-history.md has the full mapping.
         */
        private val OLDER_SECTION_NAMES = mapOf(
            "emb-persisted-config-init" to "emb-persisted-config-load",
            "emb-setup-services" to "emb-post-init",
        )

        /** The values of the section [name] was called before a rename, removed from this map with that name. */
        private fun MutableMap<String, List<Double>>.removeUnderOlderName(name: String): Pair<String, List<Double>>? {
            val oldName = OLDER_SECTION_NAMES[name] ?: return null
            return remove(oldName)?.let { oldName to it }
        }
    }
}
