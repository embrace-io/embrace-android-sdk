package io.embrace.startup.analysis

import io.embrace.startup.core.json.PyJson
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.stats.Quantile
import io.embrace.startup.core.text.PyFormat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

/**
 * `trend`: baselines, drift and regressions per series from the longitudinal store.
 *
 * Design choices carried over intact:
 * - a series is one SDK VERSION on one device under one recipe and one set of conditions; nothing is
 *   ever averaged across series (a store that grouped 9.1.0 with 9.0.0 once reported the OLDER
 *   version as a "+10.7% candidate regression" - a version comparison in the clothes of drift);
 * - the baseline is FIXED (the earliest N published-version runs), never rolling, because a rolling
 *   baseline absorbs a slow regression and reports "no change" all the way down;
 * - significance is judged against the baseline's own run-to-run spread of medians (floored at 4%);
 * - median and tail are separate verdicts;
 * - version-to-version differences are a separate table and are never called regressions.
 */
object TrendReport {

    const val DEFAULT_BASELINE_RUNS: Int = 3
    const val BAND_FLOOR_PCT: Double = 4.0

    data class SeriesKey(
        val device: String,
        val sdkVersion: String,
        val build: String,
        val compileState: String,
        val instrument: String,
        val conditions: String,
    ) : Comparable<SeriesKey> {
        override fun compareTo(other: SeriesKey): Int = compareValuesBy(
            this,
            other,
            { it.device },
            { it.sdkVersion },
            { it.build },
            { it.compileState },
            { it.instrument },
            { it.conditions },
        )

        fun joined(): String = listOf(device, sdkVersion, build, compileState, instrument, conditions).joinToString("|")
    }

    data class SeriesOut(
        val baselineMedian: Double,
        val bandPct: Double,
        val latestDeltaPct: Double,
        val runs: Int,
        val nextAction: String,
    )

    data class Result(val text: String, val json: Map<String, SeriesOut>)

    /** Store lines as loose records; a malformed line is skipped, with the message the frozen goldens expect. */
    fun readStore(path: Path): Pair<List<JsonObject>, List<String>> {
        val records = ArrayList<JsonObject>()
        val notes = ArrayList<String>()
        Files.readAllLines(path).forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            try {
                records.add(StartupJson.parseToJsonElement(line).jsonObject)
            } catch (e: SerializationException) {
                notes.add("skipping a malformed store line")
            } catch (e: IllegalArgumentException) {
                notes.add("skipping a malformed store line")
            }
        }
        return records to notes
    }

    fun groupKey(rec: JsonObject): SeriesKey {
        val recipe = PyJson.obj(rec, "recipe")
        val conditions = rec["conditions"]?.takeIf { it is JsonObject } as JsonObject? ?: JsonObject(emptyMap())
        return SeriesKey(
            device = PyJson.str(rec, "device_key", "unknown"),
            sdkVersion = PyJson.str(rec, "sdk_version", "?"),
            build = PyJson.str(recipe, "build_type", "?"),
            compileState = PyJson.str(recipe, "compile_state", "?"),
            instrument = PyJson.str(recipe, "instrument", "?"),
            conditions = PyJson.dumps(conditions),
        )
    }

    fun verdict(deltaPct: Double, bandPct: Double, reproduced: Boolean): String = when {
        abs(deltaPct) <= bandPct -> "noise"
        !reproduced -> "candidate (re-run to confirm)"
        deltaPct > 0 -> "REGRESSION"
        else -> "improvement (confirmed)"
    }

    /** Signals established in >= [minPrior] earlier runs and absent now, and signals never seen before. */
    fun signalChanges(runs: List<JsonObject>, minPrior: Int = 2): Pair<List<String>, List<String>> {
        if (runs.size < minPrior + 1) return emptyList<String>() to emptyList()
        val latest = signals(runs.last()).toSet()
        val earlier = runs.dropLast(1)
        if (latest.isEmpty() && earlier.none { signals(it).isNotEmpty() }) return emptyList<String>() to emptyList()
        val priorCounts = HashMap<String, Int>()
        earlier.forEach { run -> signals(run).toSet().forEach { priorCounts[it] = (priorCounts[it] ?: 0) + 1 } }
        val established = priorCounts.filterValues { it >= minPrior }.keys
        return (established - latest).sorted() to (latest - priorCounts.keys).sorted()
    }

    fun run(records: List<JsonObject>, baselineRuns: Int = DEFAULT_BASELINE_RUNS, notes: List<String> = emptyList()): Result {
        val out = StringBuilder()
        notes.forEach { out.line(it) }
        val json = LinkedHashMap<String, SeriesOut>()
        val groups = records.filter { PyJson.truthy(PyJson.obj(it, "derived"), "n") }
            .groupBy { groupKey(it) }
            .toSortedMap()
        groups.forEach { (key, unsorted) ->
            out.series(key, unsorted.sortedBy { timestamp(it) }, baselineRuns)?.let { json[key.joined()] = it }
        }
        if (groups.isEmpty()) out.line("store has no usable records yet")
        out.versionComparison(records)
        out.line(
            "\nReminders: baselines are fixed, not rolling (a rolling baseline hides slow drift); " +
                "one run is a candidate, two make a regression; never compare across device keys or " +
                "recipes.",
        )
        return Result(out.toString(), json)
    }

    /** The fixed baseline of a series and the band it implies. */
    private class Baseline(val runs: List<JsonObject>, val median: Double, val p90: Double, val bandPct: Double) {
        fun deltaMedian(rec: JsonObject) = pctDelta(derived(rec, "median"), median)
        fun deltaP90(rec: JsonObject) = pctDelta(derived(rec, "p90"), p90)
    }

    private fun StringBuilder.line(s: String = ""): StringBuilder = append(s).append('\n')

    private fun StringBuilder.series(key: SeriesKey, allRuns: List<JsonObject>, baselineRuns: Int): SeriesOut? {
        line(
            "\n${"=".repeat(RULE)}\n${key.device}  sdk=${key.sdkVersion}  [build=${key.build} " +
                "compile=${key.compileState} instrument=${key.instrument}]\n  conditions=${key.conditions}" +
                "\n  runs=${allRuns.size}",
        )
        if (allRuns.size < 2) {
            line("  only one run in this series - no trend yet; repeat the same recipe to start a baseline")
            return null
        }
        val eligible = allRuns.filter { PyJson.bool(it, "baseline_eligible", true) }
        val adhoc = allRuns.filter { !PyJson.bool(it, "baseline_eligible", true) }
        if (eligible.isEmpty()) {
            line(
                "  no published-version runs in this series - cannot form a baseline. Run the " +
                    "reference cell against a released SDK version first; working-tree runs are " +
                    "comparison-only.",
            )
            return null
        }
        val base = baselineOf(eligible, baselineRuns)
        line(
            "  baseline (first ${base.runs.size} run(s)): median ${PyFormat.fixed(base.median, 1)} ms, " +
                "p90 ${PyFormat.fixed(base.p90, 1)} ms, noise band +-${PyFormat.fixed(base.bandPct, 0)}%",
        )
        val deltas = runsTable(eligible, base)
        pooledLine(eligible)
        adhocBlock(adhoc, base)
        signalsBlock(eligible)
        val action = nextAction(deltas, base.bandPct)
        line("  next action: $action")
        return SeriesOut(base.median, base.bandPct, deltas.last(), eligible.size, action)
    }

    private fun baselineOf(runs: List<JsonObject>, baselineRuns: Int): Baseline {
        val baseline = runs.take(maxOf(1, minOf(baselineRuns, runs.size - 1)))
        val medians = baseline.map { derived(it, "median") }
        val median = Quantile.median(medians.sorted())
        val p90 = Quantile.median(baseline.map { derived(it, "p90") }.sorted())
        val band = if (median != 0.0) {
            maxOf(BAND_FLOOR_PCT, PERCENT * (medians.max() - medians.min()) / median)
        } else {
            BAND_FLOOR_PCT
        }
        return Baseline(baseline, median, p90, band)
    }

    /** The per-run table; returns the median deltas in run order. */
    private fun StringBuilder.runsTable(runs: List<JsonObject>, base: Baseline): List<Double> {
        line(
            "  ${"measured_at".padEnd(W21)}${"n".padStart(W5)}${"median".padStart(W9)}${"p90".padStart(W8)}${"max".padStart(W8)}" +
                "${"d-median".padStart(W10)}${"d-p90".padStart(W8)}  verdict",
        )
        val band = base.bandPct
        val deltas = ArrayList<Double>()
        runs.forEachIndexed { i, r ->
            val dm = base.deltaMedian(r)
            val dp = base.deltaP90(r)
            deltas.add(dm)
            val reproduced = i > 0 && abs(deltas[i - 1]) > band && (deltas[i - 1] > 0) == (dm > 0) && abs(dm) > band
            val tag = when {
                i < base.runs.size -> "baseline"
                abs(dp) > band && abs(dm) <= band ->
                    verdict(dm, band, reproduced) + " | TAIL-ONLY move (p90) - users feel this even when the median does not"
                else -> verdict(dm, band, reproduced)
            }
            line(
                "  ${timestamp(r).ifEmpty { "?" }.take(TS_LEN).padEnd(W21)}" +
                    "${PyJson.str(PyJson.obj(r, "derived"), "n", "").padStart(W5)}${f1(derived(r, "median"), W9)}" +
                    "${f1(derived(r, "p90"), W8)}${f1(derived(r, "max"), W8)}" +
                    "${PyFormat.fixedSigned(dm, 1).padStart(W9)}%${PyFormat.fixedSigned(dp, 1).padStart(W7)}%  $tag",
            )
        }
        return deltas
    }

    private fun StringBuilder.pooledLine(runs: List<JsonObject>) {
        val pooled = runs.flatMap { r ->
            PyJson.arr(r, "windows_ms")?.map {
                it.jsonPrimitive.content.toDouble()
            } ?: emptyList()
        }
            .sorted()
        if (pooled.isEmpty()) return
        val p99 = if (pooled.size < P99_MIN_N) {
            "  p99 needs more samples than this"
        } else {
            "  p99 ${PyFormat.fixed(Quantile.legacyIndex(pooled, P99), 1)}"
        }
        line(
            "  pooled across this series (n=${pooled.size}): median " +
                "${PyFormat.fixed(Quantile.median(pooled), 1)}  p90 ${PyFormat.fixed(Quantile.legacyIndex(pooled, P90), 1)}  " +
                "p95 ${PyFormat.fixed(Quantile.legacyIndex(pooled, P95), 1)}  max ${PyFormat.fixed(pooled.last(), 1)}$p99",
        )
    }

    private fun StringBuilder.adhocBlock(adhoc: List<JsonObject>, base: Baseline) {
        if (adhoc.isEmpty()) return
        line("  ad-hoc comparisons vs this baseline (NOT part of it): ${adhoc.size}")
        adhoc.forEach { r ->
            val dm = base.deltaMedian(r)
            val dp = base.deltaP90(r)
            val inside = if (abs(dm) <= base.bandPct) "within" else "OUTSIDE"
            line(
                "    ${PyJson.str(r, "measured_at", "?").take(TS_LEN)}  ${PyJson.str(r, "sdk_version", "local").padEnd(W22)}" +
                    " median ${f1(derived(r, "median"), W7)} (${PyFormat.fixedSigned(dm, 1)}%)  " +
                    "p90 ${f1(derived(r, "p90"), W7)} (${PyFormat.fixedSigned(dp, 1)}%)" +
                    "  $inside the baseline noise band",
            )
        }
        line(
            "    (a working-tree run is judged against the published baseline, never merged " +
                "into it; confirm any move with a second run before believing it)",
        )
    }

    private fun StringBuilder.signalsBlock(runs: List<JsonObject>) {
        val (disappeared, appeared) = signalChanges(runs)
        if (disappeared.isNotEmpty()) {
            line("  SIGNALS DISAPPEARED (present in >=2 earlier runs, absent now): ${disappeared.joinToString(", ")}")
            line(
                "    -> data-integrity finding, NOT an improvement. Check trace health/capture " +
                    "config, then the build and instrument, before trusting this run's numbers.",
            )
        }
        if (appeared.isNotEmpty()) {
            line("  signals newly present: ${appeared.joinToString(", ")}")
            line(
                "    -> an instrument or recipe change, not a regression; re-baseline " +
                    "deliberately if this is intended.",
            )
        }
    }

    private fun nextAction(deltas: List<Double>, band: Double): String {
        val latest = deltas.last()
        val previous = deltas.getOrNull(deltas.size - 2)
        return when {
            abs(latest) <= band -> "no action - within the noise band"
            previous != null && abs(previous) > band && (previous > 0) == (latest > 0) ->
                "CONFIRMED move: escalate into startup-version-factor-matrix to find which factor carries it"
            else -> "re-run this cell with the same recipe to confirm or dismiss"
        }
    }

    private data class Cell(
        val device: String,
        val build: String,
        val compile: String,
        val instrument: String,
    ) : Comparable<Cell> {
        override fun compareTo(other: Cell): Int =
            compareValuesBy(this, other, { it.device }, { it.build }, { it.compile }, { it.instrument })
    }

    private data class VersionRow(val version: String, val median: Double, val totalN: Long, val runs: Int)

    private fun StringBuilder.versionComparison(records: List<JsonObject>) {
        val byCell = LinkedHashMap<Cell, LinkedHashMap<String, MutableList<JsonObject>>>()
        records.forEach { rec ->
            val derived = PyJson.obj(rec, "derived")
            if (!PyJson.truthy(derived, "n")) return@forEach
            val recipe = PyJson.obj(rec, "recipe")
            val cell = Cell(
                PyJson.str(rec, "device_key", "unknown"),
                PyJson.str(recipe, "build_type", "?"),
                PyJson.str(recipe, "compile_state", "?"),
                PyJson.str(recipe, "instrument", "?"),
            )
            byCell.getOrPut(cell) { LinkedHashMap() }.getOrPut(PyJson.str(rec, "sdk_version", "?")) { ArrayList() }
                .add(checkNotNull(derived))
        }
        val multi = byCell.filterValues { it.size > 1 }.toSortedMap()
        if (multi.isEmpty()) return
        line("\n" + "=".repeat(RULE))
        line("VERSION COMPARISON - different SDK versions on the same device and recipe.")
        line(
            "Separate from the drift report above: a difference here is deliberate (the SDK " +
                "changed), not drift. With one run per version it is a candidate finding, not a " +
                "verdict - repeat both versions before calling it real.",
        )
        multi.forEach { (cell, versions) ->
            line("\n${cell.device}  [build=${cell.build} compile=${cell.compile} instrument=${cell.instrument}]")
            val rows = versions.keys.sorted().mapNotNull { version ->
                val ds = versions.getValue(version)
                val medians = ds.mapNotNull { d -> PyJson.double(d, "median")?.takeIf { it != 0.0 } }
                val totalN = ds.sumOf { d -> PyJson.double(d, "n")?.toLong() ?: 0L }
                if (medians.isEmpty()) null else VersionRow(version, Quantile.median(medians.sorted()), totalN, ds.size)
            }
            val newest = rows.maxByOrNull { it.version } ?: return@forEach
            rows.forEach { row ->
                val delta = if (row.version == newest.version) {
                    ""
                } else {
                    "   ${PyFormat.fixedSigned(PERCENT * (row.median - newest.median) / newest.median, 1)}% vs ${newest.version}"
                }
                line(
                    "  ${row.version.padEnd(W12)} median ${f1(row.median, W7)} ms   n=${row.totalN.toString().padEnd(W5)} " +
                        "runs=${row.runs}$delta",
                )
            }
        }
    }

    private fun signals(rec: JsonObject): List<String> =
        PyJson.arr(rec, "signals_present")?.map { it.jsonPrimitive.content } ?: emptyList()

    private fun timestamp(rec: JsonObject): String =
        PyJson.strOrNull(rec, "measured_at") ?: PyJson.strOrNull(rec, "ingested_at") ?: ""

    private fun derived(rec: JsonObject, key: String): Double =
        requireNotNull(PyJson.double(PyJson.obj(rec, "derived"), key)) { "record lacks derived.$key" }

    private fun pctDelta(value: Double, base: Double): Double = if (base != 0.0) PERCENT * (value - base) / base else 0.0

    private fun f1(x: Double, width: Int) = PyFormat.fixed(x, 1).padStart(width)

    private const val RULE = 78
    private const val PERCENT = 100.0
    private const val TS_LEN = 19
    private const val P90 = 0.90
    private const val P95 = 0.95
    private const val P99 = 0.99
    private const val P99_MIN_N = 500
    private const val W5 = 5
    private const val W7 = 7
    private const val W8 = 8
    private const val W9 = 9
    private const val W10 = 10
    private const val W12 = 12
    private const val W21 = 21
    private const val W22 = 22
}
