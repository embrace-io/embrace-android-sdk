package io.embrace.startup.analysis

import io.embrace.startup.core.json.PyJson
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.stats.Quantile
import io.embrace.startup.core.text.PyFormat
import io.embrace.startup.perfetto.Queries
import io.embrace.startup.perfetto.TraceProcessor
import io.embrace.startup.perfetto.TraceReads
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/**
 * `matrix_report.py`: cross-cell comparison for a version × factor run. Per cell: n, window
 * median/p90/max, per-pass medians (so pass-state stays visible instead of pooled away); then the
 * version table (reference cells only) and the factor table (effect per anchor version, against the
 * same version's reference cell).
 *
 * No Python golden exists for this report (it needs cell-state run directories, none survived), so
 * the port is checked against hand-derived expectations on a synthetic run directory. Departure
 * (port log): the default window slice is `emb-sdk-start`, with `composed` for pre-9.2.0 cells; the
 * Python defaulted to `app-embrace-start`, a slice the harness never emitted.
 */
object MatrixReport {

    const val DEFAULT_SLICE: String = "emb-sdk-start"

    data class Summary(
        val n: Int,
        val median: Double,
        val p90: Double,
        val max: Double,
        val iqr: Double,
        /** Pass key → median, keys sorted as strings (`pass1`, `pass10`, `pass2`, …) as the Python did. */
        val passMedians: Map<String, Double>,
    )

    data class CellResult(val cell: JsonObject, val summary: Summary, val buildType: String?, val apkSha12: String)

    /** Window values grouped by pass (the first path element starting with `pass`, else the parent dir). */
    fun cellWindows(cellDir: Path, readWindow: (Path) -> Double?): Map<String, List<Double>> {
        val passes = LinkedHashMap<String, MutableList<Double>>()
        val traces = Files.walk(cellDir).use { s ->
            s.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".perfetto-trace") }.toList()
        }.sorted()
        traces.forEach { trace ->
            val key = trace.map { it.toString() }.firstOrNull { it.startsWith("pass") } ?: trace.parent.fileName.toString()
            val value = readWindow(trace)
            if (value != null && value != 0.0) passes.getOrPut(key) { ArrayList() }.add(value)
        }
        return passes
    }

    /** `summarize`: index-based p90 and IQR (the store's legacy definitions), pass medians in key order. */
    fun summarize(passes: Map<String, List<Double>>): Summary? {
        val all = passes.values.flatten().sorted()
        if (all.isEmpty()) return null
        val n = all.size
        val iqr = if (n >= MIN_N_FOR_IQR) all[(P75 * n).toInt()] - all[(P25 * n).toInt()] else 0.0
        return Summary(
            n = n,
            median = Quantile.median(all),
            p90 = Quantile.legacyIndex(all, P90),
            max = all.last(),
            iqr = iqr,
            passMedians = passes.toSortedMap().filterValues { it.isNotEmpty() }.mapValues { Quantile.median(it.value.sorted()) },
        )
    }

    /** Every `cell-state.json` under [runDir], measured; skipped cells are reported in the returned lines. */
    fun collect(
        runDir: Path,
        readWindow: (Path) -> Double?,
        slice: String,
    ): Pair<Map<String, CellResult>, List<String>> {
        val cells = LinkedHashMap<String, CellResult>()
        val notes = ArrayList<String>()
        val states = Files.walk(runDir).use { s ->
            s.filter { Files.isRegularFile(it) && it.fileName.toString() == "cell-state.json" }.toList()
        }.sorted()
        states.forEach { stateFile ->
            val state = StartupJson.parseToJsonElement(Files.readString(stateFile)).jsonObject
            val cell = state.getValue("cell").jsonObject
            val id = PyJson.str(cell, "id", "?")
            val summary = summarize(cellWindows(stateFile.parent, readWindow))
            if (summary == null) {
                notes.add("SKIP $id: no window values ($slice missing?)")
                return@forEach
            }
            val sha = (PyJson.strOrNull(state, "apk_sha256") ?: "").take(SHA_CHARS)
            cells[id] = CellResult(cell, summary, PyJson.strOrNull(state, "build_type"), sha)
        }
        return cells to notes
    }

    fun windowReader(tp: TraceProcessor, slice: String): (Path) -> Double? = { trace ->
        TraceReads.parseWindow(tp.queryRaw(Queries.windowFor(slice), trace).stdout)
    }

    fun report(cells: Map<String, CellResult>, slice: String, notes: List<String> = emptyList()): String {
        val out = StringBuilder()
        notes.forEach { out.line(it) }
        out.line("\ninstrument: $slice   (absolute values are build-type specific)\n")
        out.line("${"cell".padEnd(W58)}${"n".padStart(W5)}${"med".padStart(W9)}${"p90".padStart(W9)}${"max".padStart(W9)}  pass medians")
        cells.toSortedMap().forEach { (cid, c) ->
            val s = c.summary
            val pm = s.passMedians.values.joinToString(" ") { PyFormat.fixed(it, 0) }
            var flag = ""
            if (s.passMedians.size > 1 && s.iqr != 0.0) {
                val spread = s.passMedians.values.max() - s.passMedians.values.min()
                if (spread > s.iqr) flag = "  <-- PASS-STATE: re-judge on same-parity passes"
            }
            out.line("${cid.padEnd(W58)}${s.n.toString().padStart(W5)}${f1(s.median, W9)}${f1(s.p90, W9)}${f1(s.max, W9)}  $pm$flag")
        }
        out.versionTable(cells)
        out.factorTable(cells)
        out.line(
            "\nReminders: pair every window delta with TTID and pre-TTID main-thread CPU (a window " +
                "win with flat pre-TTID CPU is re-attribution, not improvement); never compare across " +
                "build types or devices except as labelled tier replication.",
        )
        return out.toString()
    }

    /** `--json`: the cells dict as the Python dumped it (summary, build type, short sha). */
    fun toJson(cells: Map<String, CellResult>): JsonObject = JsonObject(
        cells.mapValues { (_, c) ->
            val s = c.summary
            JsonObject(
                mapOf(
                    "cell" to c.cell,
                    "summary" to JsonObject(
                        mapOf(
                            "n" to JsonPrimitive(s.n),
                            "median" to JsonPrimitive(s.median),
                            "p90" to JsonPrimitive(s.p90),
                            "max" to JsonPrimitive(s.max),
                            "iqr" to JsonPrimitive(s.iqr),
                            "pass_medians" to JsonObject(s.passMedians.mapValues { JsonPrimitive(it.value) as JsonElement }),
                        ),
                    ),
                    "build_type" to (c.buildType?.let { JsonPrimitive(it) } ?: JsonNull),
                    "apk_sha256" to JsonPrimitive(c.apkSha12),
                ),
            )
        },
    )

    private fun StringBuilder.line(s: String = ""): StringBuilder = append(s).append('\n')

    private fun StringBuilder.versionTable(cells: Map<String, CellResult>) {
        val ref = cells.filterKeys { it.endsWith("|reference") }
        if (ref.size <= 1) return
        line("\n=== VERSION TABLE (reference cell) ===")
        // The Python's `max(ref, key=version == "local")`: a local cell if any, else the first key.
        val newest = ref.keys.firstOrNull { PyJson.strOrNull(ref.getValue(it).cell, "version") == "local" } ?: ref.keys.first()
        val base = ref.getValue(newest).summary.median
        line("${"version".padEnd(W14)}${"med".padStart(W9)}${"p90".padStart(W9)}${"max".padStart(W9)}${"delta vs newest".padStart(W18)}")
        ref.entries.sortedBy { it.value.summary.median }.forEach { (_, c) ->
            val s = c.summary
            val d = s.median - base
            if (base != 0.0) {
                line(
                    "${PyJson.str(c.cell, "version", "None").padEnd(W14)}${f1(s.median, W9)}${f1(s.p90, W9)}${f1(s.max, W9)}" +
                        "${PyFormat.fixedSigned(d, 1).padStart(W13)} ms (${PyFormat.fixedSigned(PERCENT * d / base, 0)}%)",
                )
            } else {
                line("")
            }
        }
    }

    private fun StringBuilder.factorTable(cells: Map<String, CellResult>) {
        val factor = cells.filterKeys { !it.endsWith("|reference") }
        if (factor.isEmpty()) return
        line("\n=== FACTOR TABLE (vs the same version's reference cell) ===")
        line(
            "${"version".padEnd(W14)}${"factor level".padEnd(W30)}${"med".padStart(W9)}${"ref med".padStart(W10)}${"effect".padStart(W20)}",
        )
        factor.toSortedMap().forEach { (cid, c) ->
            val version = PyJson.str(c.cell, "version", "None")
            val device = PyJson.str(c.cell, "device", "None")
            val level = cid.substringAfterLast("|")
            val refCell = cells["$device|$version|reference"]
            val m = c.summary.median
            if (refCell == null) {
                line("${version.padEnd(W14)}${level.padEnd(W30)}${f1(m, W9)}${"--".padStart(W10)}${"no reference cell".padStart(W20)}")
                return@forEach
            }
            val rm = refCell.summary.median
            line(
                "${version.padEnd(W14)}${level.padEnd(W30)}${f1(m, W9)}${f1(rm, W10)}" +
                    "${PyFormat.fixedSigned(m - rm, 1).padStart(W13)} ms (${PyFormat.fixedSigned(PERCENT * (m - rm) / rm, 0)}%)",
            )
        }
        line(
            "\nParallel effects across anchor versions = factor effects. Effects that change " +
                "sign or magnitude materially = version x factor interactions worth investigating.",
        )
    }

    private fun f1(x: Double, width: Int) = PyFormat.fixed(x, 1).padStart(width)

    private const val P90 = 0.9
    private const val P75 = 0.75
    private const val P25 = 0.25
    private const val MIN_N_FOR_IQR = 4
    private const val SHA_CHARS = 12
    private const val PERCENT = 100.0
    private const val W5 = 5
    private const val W9 = 9
    private const val W10 = 10
    private const val W13 = 13
    private const val W14 = 14
    private const val W18 = 18
    private const val W20 = 20
    private const val W30 = 30
    private const val W58 = 58
}
