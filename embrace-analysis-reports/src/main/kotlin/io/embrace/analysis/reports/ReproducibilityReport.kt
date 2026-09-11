package io.embrace.analysis.reports

import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.common.text.PyFormat
import io.embrace.analysis.stats.Quantile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

/**
 * `reproducibility`: do independent contributors on the same model agree?
 *
 * Verdicts are per statistic (median / tail / shape) because they fail for different reasons and a
 * single "agrees" hides the informative case. Tolerance is derived from the submissions' OWN
 * within-run spread (floored at 5%), never a fixed percentage. Where a cell disagrees, provenance
 * DIFFS are ranked as candidate explanations in [HUNT_FIELDS] order; a disagreeing cell is never
 * averaged.
 */
object ReproducibilityReport {

    val HUNT_FIELDS: List<String> = listOf(
        "recipe", "installed_app_count", "os_build", "conditions", "gate_temp_c", "battery_health", "storage_free_pct",
        "device_settings", "kernel_version", "security_patch", "skin_version", "tool_versions",
    )
    const val TOLERANCE_FLOOR_PCT: Double = 5.0

    data class CellKey(val model: String, val osBuild: String, val sdk: String, val recipe: String, val conditions: String) :
        Comparable<CellKey> {
        override fun compareTo(other: CellKey): Int =
            compareValuesBy(this, other, { it.model }, { it.osBuild }, { it.sdk }, { it.recipe }, { it.conditions })

        fun joined(): String = listOf(model, osBuild, sdk, recipe, conditions).joinToString("|")
    }

    sealed interface CellOut {
        data class Insufficient(val submissions: Int) : CellOut
        data class Judged(
            val status: String,
            val medianSpreadPct: Double,
            val p90SpreadPct: Double,
            val tolerancePct: Double,
            val contributors: Int,
            val units: Int,
            val candidates: List<String>,
        ) : CellOut
    }

    data class Result(val text: String, val json: Map<String, CellOut>)

    fun readCorpus(path: Path): Pair<List<JsonObject>, List<String>> {
        val records = ArrayList<JsonObject>()
        val notes = ArrayList<String>()
        Files.readAllLines(path).forEach { raw ->
            if (raw.isBlank()) return@forEach
            try {
                records.add(StartupJson.parseToJsonElement(raw).jsonObject)
            } catch (e: SerializationException) {
                notes.add("skipping a malformed corpus line")
            } catch (e: IllegalArgumentException) {
                notes.add("skipping a malformed corpus line")
            }
        }
        return records to notes
    }

    fun cellKey(rec: JsonObject): CellKey {
        val recipe = PyJson.obj(rec, "recipe")
        return CellKey(
            model = PyJson.str(rec, "model", "?"),
            osBuild = PyJson.str(rec, "os_build", "?"),
            sdk = PyJson.str(rec, "sdk_version", "?"),
            recipe = "${pyStr(recipe?.get("build_type"))}/${pyStr(recipe?.get("compile_state"))}/${pyStr(recipe?.get("instrument"))}",
            conditions = PyJson.dumps(rec["conditions"]?.takeIf { it is JsonObject } ?: JsonObject(emptyMap())),
        )
    }

    /** Within-submission spread of pass medians as a percentage of the median - the basis for tolerance. */
    fun ownSpreadPct(rec: JsonObject): Double? {
        val passes = passMedians(rec)
        val median = PyJson.double(PyJson.obj(rec, "derived"), "median")
        if (passes.size < 2 || median == null || median == 0.0) return null
        return PERCENT * (passes.max() - passes.min()) / median
    }

    /** Two-state (compile-state alternation) or unimodal, from the gaps between sorted pass medians. */
    fun shapeOf(rec: JsonObject): String {
        val passes = passMedians(rec).sorted()
        val median = PyJson.double(PyJson.obj(rec, "derived"), "median")
        if (passes.size < MIN_PASSES_FOR_SHAPE || median == null || median == 0.0) return "unknown"
        val gap = passes.zipWithNext { a, b -> b - a }.max()
        return if (gap > TWO_STATE_GAP * median) "two-state" else "unimodal"
    }

    /** Distinct rendered values of a provenance field across submissions, sorted, or null when they agree. */
    fun diffs(recs: List<JsonObject>, field: String): List<String>? {
        val values = recs.map { r ->
            val el = r[field]
            if (el is JsonObject || el is JsonArray) PyJson.dumps(el) else pyStr(el)
        }.toSet()
        return if (values.size > 1) values.sorted() else null
    }

    fun run(records: List<JsonObject>, notes: List<String> = emptyList()): Result {
        val out = StringBuilder()
        notes.forEach { out.line(it) }
        val json = LinkedHashMap<String, CellOut>()
        val cells = records.filter { PyJson.truthy(PyJson.obj(it, "derived"), "n") }.groupBy { cellKey(it) }.toSortedMap()
        cells.forEach { (key, recs) -> json[key.joined()] = out.cell(key, recs) }
        if (cells.isEmpty()) out.line("corpus has no usable records yet")
        out.line(
            "\nReminders: pool only inside a reproduced cell; never pool across models to make a " +
                "fleet number (composition artefact); never widen tolerance to force agreement - that " +
                "disables the only mechanism that finds unaccounted dimensions.",
        )
        return Result(out.toString(), json)
    }

    private fun StringBuilder.line(s: String = ""): StringBuilder = append(s).append('\n')

    private fun StringBuilder.cell(key: CellKey, recs: List<JsonObject>): CellOut {
        val contributors = recs.map { it["contributor"] }.toSet()
        val units = recs.map { it["unit_id"] }.toSet()
        val schemaVersions = recs.map { it["schema_version"] }.toSet()
        line(
            "\n${"=".repeat(RULE)}\n${key.model}  sdk=${key.sdk}\n  build=${key.osBuild}\n  recipe=${key.recipe} " +
                "conditions=${key.conditions}",
        )
        line(
            "  submissions=${recs.size} contributors=${contributors.size} units=${units.size}" +
                if (schemaVersions.size > 1) "  schema_versions=${reprSorted(schemaVersions)}" else "",
        )
        if (contributors.size < 2 && units.size < 2) {
            line("  insufficient data: needs a second contributor or unit before reproducibility can be tested at all")
            return CellOut.Insufficient(recs.size)
        }
        val medians = recs.map { derived(it, "median") }
        val p90s = recs.map { derived(it, "p90") }
        val spreads = recs.mapNotNull { ownSpreadPct(it) }
        val tol = maxOf(TOLERANCE_FLOOR_PCT, spreads.maxOrNull() ?: TOLERANCE_FLOOR_PCT)
        val medSpread = PERCENT * (medians.max() - medians.min()) / Quantile.median(medians.sorted())
        val p90Spread = PERCENT * (p90s.max() - p90s.min()) / Quantile.median(p90s.sorted())
        val shapes = recs.map { shapeOf(it) }.toSet()
        val medV = if (medSpread <= tol) "agree" else "MEDIANS DIFFER"
        val p90V = if (p90Spread <= tol) "agree" else "TAILS DIFFER"
        val shapeV = if ((shapes - "unknown").size <= 1) "agree" else "SHAPE DIFFERS"
        line("  tolerance from own within-run spread: +-${PyFormat.fixed(tol, 0)}%")
        line("  median  spread ${PyFormat.fixed(medSpread, 1).padStart(W5)}%  -> $medV")
        line("  p90     spread ${PyFormat.fixed(p90Spread, 1).padStart(W5)}%  -> $p90V")
        line("  shape   ${PyJson.reprList(shapes.sorted())}  -> $shapeV")

        val reproduced = listOf(medV, p90V, shapeV).all { it == "agree" }
        val candidates = if (reproduced) {
            reproducedLine(recs, contributors.size, units.size)
            emptyList()
        } else {
            candidatesBlock(recs)
        }
        val status = when {
            reproduced -> "reproduced"
            candidates.isEmpty() -> "unresolved"
            else -> "unresolved (candidates found)"
        }
        return CellOut.Judged(status, medSpread, p90Spread, tol, contributors.size, units.size, candidates)
    }

    private fun StringBuilder.reproducedLine(recs: List<JsonObject>, contributors: Int, units: Int) {
        val pooled = recs.flatMap { windowsOf(it) }.sorted()
        if (pooled.isEmpty()) return
        val tail = if (pooled.size >= P99_MIN_N) {
            " p99 ${PyFormat.fixed(Quantile.legacyIndex(pooled, P99), 1)}"
        } else {
            " (n<500: no p99)"
        }
        line(
            "  REPRODUCED - pooled n=${pooled.size} across $contributors " +
                "contributors/$units units: median ${PyFormat.fixed(Quantile.median(pooled), 1)} " +
                "p90 ${PyFormat.fixed(Quantile.legacyIndex(pooled, P90), 1)} " +
                "p95 ${PyFormat.fixed(Quantile.legacyIndex(pooled, P95), 1)}$tail",
        )
    }

    /** Prints the dimension hunt and returns the differing fields in [HUNT_FIELDS] order. */
    private fun StringBuilder.candidatesBlock(recs: List<JsonObject>): List<String> {
        val candidates = HUNT_FIELDS.mapNotNull { f -> diffs(recs, f)?.let { f to it } }
        line("  NOT REPRODUCED - do not pool, do not average. Candidate dimensions, most common cause first:")
        if (candidates.isEmpty()) {
            line(
                "    - none: every recorded dimension matches, so the responsible dimension " +
                    "is NOT yet in the schema. Mark unresolved and hunt it (see " +
                    "references/reproducibility.md); if found, it becomes a required field.",
            )
        }
        candidates.take(MAX_CANDIDATES).forEach { (field, values) ->
            line("    - $field: ${PyJson.reprList(values.take(MAX_VALUES).map { it.take(VALUE_CHARS) })}")
        }
        return candidates.map { it.first }
    }

    private fun passMedians(rec: JsonObject): List<Double> =
        PyJson.obj(PyJson.obj(rec, "derived"), "pass_medians")?.values?.map {
            it.jsonPrimitive.content.toDouble()
        } ?: emptyList()

    private fun windowsOf(rec: JsonObject): List<Double> =
        PyJson.arr(rec, "windows_ms")?.map { it.jsonPrimitive.content.toDouble() } ?: emptyList()

    private fun derived(rec: JsonObject, key: String): Double =
        requireNotNull(PyJson.double(PyJson.obj(rec, "derived"), key)) { "record lacks derived.$key" }

    /** Python `str()` of a JSON scalar: `None`, `True`/`False`, numbers as written, strings verbatim. */
    private fun pyStr(el: JsonElement?): String = when {
        el == null || el is JsonNull -> "None"
        el is JsonPrimitive && el.isString -> el.content
        el is JsonPrimitive && el.content == "true" -> "True"
        el is JsonPrimitive && el.content == "false" -> "False"
        el is JsonPrimitive -> el.content
        else -> PyJson.dumps(el)
    }

    /** `sorted(set_of_scalars)` rendered as Python would: strings quoted, numbers bare, None last-ish. */
    private fun reprSorted(values: Set<JsonElement?>): String {
        val rendered = values.map { el ->
            if (el is JsonPrimitive && el.isString) PyJson.repr(el.content) else pyStr(el)
        }.sorted()
        return rendered.joinToString(", ", "[", "]")
    }

    private const val RULE = 78
    private const val PERCENT = 100.0
    private const val MIN_PASSES_FOR_SHAPE = 4
    private const val TWO_STATE_GAP = 0.10
    private const val P90 = 0.90
    private const val P95 = 0.95
    private const val P99 = 0.99
    private const val P99_MIN_N = 500
    private const val MAX_CANDIDATES = 6
    private const val MAX_VALUES = 3
    private const val VALUE_CHARS = 60
    private const val W5 = 5
}
