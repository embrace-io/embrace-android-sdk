package io.embrace.startup.analysis

import io.embrace.startup.core.stats.Quantile
import io.embrace.startup.core.text.PyFormat
import io.embrace.startup.perfetto.Queries
import io.embrace.startup.perfetto.TraceProcessor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/**
 * `analyze_startup.py`: aggregate SDK startup metrics purely from macrobenchmark traces - the window
 * (native `emb-sdk-start` when the SDK emits it, 9.2.0+, else composed from modules-init start to
 * post-services-setup end), TTID, every canonical section's first-occurrence duration and share of
 * the window, and a per-iteration scheduler-contention readout.
 *
 * The report text is reproduced line for line so it can be diffed against the Python's frozen
 * output; the only lines that legitimately differ are the two header lines carrying the start time
 * and the absolute traces directory.
 */
object StartupAnalysis {

    const val CONTENTION_THRESHOLD: Double = 0.15
    private const val TOP_EXTRAS = 15

    /** Canonical init sections in execution order (see the skill's `references/sections.md`); value = nesting depth. */
    val CANONICAL_SECTIONS: List<Pair<String, Int>> = listOf(
        "emb-embrace-impl-init" to 0,
        "emb-bootstrapper-init" to 1,
        "emb-modules-init" to 0,
        "emb-persisted-config-load" to 1,
        "emb-config-service-init" to 1,
        "emb-span-service-init" to 1,
        "emb-otel-tracer-init" to 2,
        "emb-essential-service-init" to 1,
        "emb-delivery-init" to 1,
        "emb-payload-source-init" to 1,
        "emb-post-init" to 0,
        "emb-post-services-setup" to 0,
        "emb-load-instrumentation" to 1,
    )

    data class TraceMetrics(
        /** First-occurrence duration in ms of every `emb-*` slice present. Absent sections are absent, never zero. */
        val sections: Map<String, Double>,
        val windowMs: Double?,
        val windowSource: String?,
        val ttidMs: Double?,
        val waitMs: Double?,
        val runningMs: Double?,
        val cpus: Double?,
    )

    /** `extract_metrics`: the standard `(what, k, val)` rows of `startup_metrics.sql`, typed. `[NULL]` rows are dropped. */
    fun metricsOf(triples: List<TraceProcessor.Triple>): TraceMetrics {
        val sections = LinkedHashMap<String, Double>()
        val scalars = HashMap<String, Double>()
        var source: String? = null
        triples.forEach { t ->
            when {
                t.what == "window_source" -> source = t.k
                t.value == null -> Unit
                t.what == "section" -> sections[t.k] = t.value.toDouble()
                else -> scalars[t.what] = t.value.toDouble()
            }
        }
        return TraceMetrics(
            sections = sections,
            windowMs = scalars["window_ms"],
            windowSource = source,
            ttidMs = scalars["ttid_ms"],
            waitMs = scalars["wait_ms"],
            runningMs = scalars["running_ms"],
            cpus = scalars["cpus"],
        )
    }

    fun extract(tp: TraceProcessor, trace: Path): TraceMetrics =
        metricsOf(tp.triples(Queries.STARTUP_METRICS, trace))

    /** `*.perfetto-trace` files directly under `dir`, ordered by their `iterNNN` index as the Python did. */
    fun listTraces(dir: Path): List<Path> = Files.list(dir).use { stream ->
        stream.filter { it.fileName.toString().endsWith(".perfetto-trace") }.toList()
    }.sortedBy { iterIndex(it.fileName.toString()) }

    /** The `report()` body: everything after the two run-specific header lines. */
    fun report(perTrace: List<Pair<String, TraceMetrics>>, allSections: Boolean = false): String {
        val out = StringBuilder()
        val windows = perTrace.mapNotNull { it.second.windowMs }
        out.windowBlock(perTrace, windows)
        val sectionValues = collectSections(perTrace)
        out.canonicalBlock(sectionValues, windows.takeIf { it.isNotEmpty() }?.let { Quantile.median(it.sorted()) })
        sectionValues.remove("emb-sdk-start") // reported as the window, not a section
        out.extrasBlock(sectionValues, allSections)
        out.schedulingBlock(perTrace)
        return out.toString()
    }

    fun fmtStats(vals: List<Double>): String {
        if (vals.isEmpty()) return "no data"
        val sorted = vals.sorted()
        return "n=${vals.size}  min=${PyFormat.fixed(sorted.first(), 1)}  median=${PyFormat.fixed(Quantile.median(sorted), 1)}  " +
            "mean=${PyFormat.fixed(PyFormat.exactMean(vals), 1)}  max=${PyFormat.fixed(sorted.last(), 1)}"
    }

    fun iterIndex(filename: String): Int =
        ITER_INDEX.find(filename)?.groupValues?.get(1)?.toInt() ?: (1 shl NO_INDEX_SHIFT)

    fun short(filename: String): String =
        ITER_TAIL.find(filename)?.value?.removeSuffix(".perfetto-trace") ?: filename

    private fun StringBuilder.line(s: String = ""): StringBuilder = append(s).append('\n')

    private fun StringBuilder.windowBlock(perTrace: List<Pair<String, TraceMetrics>>, windows: List<Double>) {
        val sources = perTrace.map { it.second.windowSource ?: "?" }.toSet()
        val windowDesc = when (sources) {
            setOf("emb-sdk-start") ->
                "emb-sdk-start slice (wraps Embrace.start(); ~<1 ms wider than the exported emb-embrace-init span)"
            setOf("composed") ->
                "composed: modules-init start -> post-services-setup end (= exported emb-embrace-init span; " +
                    "emb-sdk-start absent in this SDK version)"
            else -> "MIXED sources across traces: ${pyList(sources.sorted())} — do not compare iterations across sources"
        }
        line("traces analyzed: ${perTrace.size}")
        line()
        line("SDK-init window, ms — source: $windowDesc")
        line("  ${fmtStats(windows)}")
        line("  per-iteration: ${windows.joinToString(", ") { PyFormat.fixed(it, 1) }}")
        line()
        line("TTID (android.startup module; anchor differs a few ms from macrobenchmark JSON), ms:")
        line("  ${fmtStats(perTrace.mapNotNull { it.second.ttidMs })}")
        line()
    }

    private fun StringBuilder.canonicalBlock(sectionValues: MutableMap<String, MutableList<Double>>, windowMedian: Double?) {
        line("canonical sections, execution order (first-occurrence slice durations, ms):")
        line(
            "  ${"section".padEnd(SECTION_W)}${"n".padStart(N_W)}${"min".padStart(NUM_W)}${"median".padStart(NUM_W)}" +
                "${"max".padStart(NUM_W)}${"% of win".padStart(PCT_W)}",
        )
        CANONICAL_SECTIONS.forEach { (name, depth) ->
            val vals = sectionValues.remove(name) ?: emptyList()
            val label = "  ".repeat(depth) + (if (depth > 0) "↳ " else "") + name
            if (vals.isEmpty()) {
                line("  ${label.padEnd(SECTION_W)}${"-- not instrumented in this SDK version --".padStart(MISSING_W)}")
            } else {
                val median = Quantile.median(vals.sorted())
                val pct = if (windowMedian != null && windowMedian != 0.0) {
                    PyFormat.fixed(median / windowMedian * PERCENT, 1)
                } else {
                    "n/a"
                }
                line("  ${label.padEnd(SECTION_W)}${statsCells(vals)}${pct.padStart(PCT_W)}")
            }
        }
        line()
    }

    private fun StringBuilder.extrasBlock(sectionValues: Map<String, MutableList<Double>>, allSections: Boolean) {
        var extras = sectionValues.entries.sortedByDescending { Quantile.median(it.value.sorted()) }
        if (!allSections) {
            extras = extras.take(TOP_EXTRAS)
        }
        if (extras.isEmpty()) return
        val shown = if (allSections) "all" else "top $TOP_EXTRAS by median"
        line("other emb-* sections ($shown; not part of the canonical breakdown):")
        extras.forEach { (name, vals) -> line("  ${name.padEnd(SECTION_W)}${statsCells(vals)}") }
        line()
    }

    private fun StringBuilder.schedulingBlock(perTrace: List<Pair<String, TraceMetrics>>) {
        line("main-thread scheduling inside the window (contention / slow-execution check):")
        line(
            "  ${"iteration".padEnd(ITER_W)}${"window".padStart(SCHED_W)}${"run".padStart(SCHED_W)}" +
                "${"wait".padStart(SCHED_W)}${"wait%".padStart(WAIT_PCT_W)}${"cpus".padStart(CPUS_W)}",
        )
        var contended = 0
        perTrace.forEach { (name, m) ->
            val window = m.windowMs ?: return@forEach
            val wait = m.waitMs ?: return@forEach
            val ratio = wait / window
            var flag = ""
            if (ratio > CONTENTION_THRESHOLD) {
                contended++
                flag = "  CONTENDED"
            }
            line(
                "  ${short(name).padEnd(ITER_W)}${fixed1(window, SCHED_W)}${fixed1(m.runningMs ?: 0.0, SCHED_W)}" +
                    "${fixed1(wait, SCHED_W)}${fixed1(ratio * PERCENT, WAIT_PCT_W - 1)}%" +
                    "${(m.cpus ?: 0.0).toInt().toString().padStart(CPUS_W)}$flag",
            )
        }
        line("  $contended/${perTrace.size} iterations contended (wait > ${PyFormat.percent0(CONTENTION_THRESHOLD)} of window).")
        line("  Two slow signatures (see references/sections.md): high wait% = scheduler contention;")
        line("  low wait% but elevated run time vs a fast pass = slower execution (core placement /")
        line("  clocks). Judge regressions on iterations that show neither.")
    }

    /** `{n:>4}{min:>9.2f}{median:>9.2f}{max:>9.2f}` for one section's values. */
    private fun statsCells(vals: List<Double>): String {
        val sorted = vals.sorted()
        return "${vals.size.toString().padStart(N_W)}${fixed2(sorted.first())}${fixed2(Quantile.median(sorted))}" +
            fixed2(sorted.last())
    }

    private fun collectSections(perTrace: List<Pair<String, TraceMetrics>>): LinkedHashMap<String, MutableList<Double>> {
        val out = LinkedHashMap<String, MutableList<Double>>()
        perTrace.forEach { (_, m) -> m.sections.forEach { (name, v) -> out.getOrPut(name) { ArrayList() }.add(v) } }
        return out
    }

    private fun fixed2(x: Double) = PyFormat.fixed(x, 2).padStart(NUM_W)

    private fun fixed1(x: Double, width: Int) = PyFormat.fixed(x, 1).padStart(width)

    /** Python's `str(list_of_str)`: `['a', 'b']`. */
    private fun pyList(items: List<String>) = items.joinToString(", ", "[", "]") { "'$it'" }

    private val ITER_INDEX = Regex("iter(\\d+)")
    private val ITER_TAIL = Regex("iter\\d+.*")
    private const val NO_INDEX_SHIFT = 30
    private const val PERCENT = 100.0
    private const val SECTION_W = 44
    private const val N_W = 4
    private const val NUM_W = 9
    private const val PCT_W = 10
    private const val MISSING_W = 41
    private const val ITER_W = 52
    private const val SCHED_W = 8
    private const val WAIT_PCT_W = 7
    private const val CPUS_W = 6
}
