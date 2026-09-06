package io.embrace.startup.campaign

import io.embrace.startup.core.json.PyJson
import io.embrace.startup.core.text.PyFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Expands a version × factor plan into an ordered, interleaved cell list with a
 * wall-clock estimate. Dry-run by nature - it prints what WOULD run so a plan that does not fit the
 * window is trimmed before any device time is spent. Trim cells, never iterations.
 *
 * Ordering: ALL reference (version-sweep) cells first, so an interrupted run still yields
 * interpretable data; then factor/combo cells round-robin across groups so no group sits entirely
 * in one thermal regime. The per-launch seconds are budgeting figures, not measurements.
 */
object MatrixPlan {

    val LAUNCH_SECONDS: Map<String, Double> = mapOf("flagship" to 7.0, "mid" to 9.0, "entry" to 20.0)
    const val DEFAULT_LAUNCH_SECONDS: Double = 10.0
    const val PASS_OVERHEAD_S: Double = 180.0
    const val COOL_GATE_S: Double = 300.0
    val REQUIRED_KEYS: List<String> = listOf(
        "run_id",
        "primary_device",
        "devices",
        "passes",
        "iterations",
        "reference",
        "versions",
        "anchors",
    )
    val DEVICE_FIELDS: List<String> = listOf("serial", "api_level", "tier", "vendor")

    const val CHECKLIST: String = """
Controls this run depends on (cell_runner enforces the machine-checkable ones):
  [runner] resolved SDK coordinate matches the cell (read back, not from the catalog file)
  [runner] compile state matches the cell (dumpsys package dexopt, recorded per cell)
  [runner] APK sha256 recorded; unexpected changes flagged (git state alters the build id)
  [runner] temperature within band before each pass, from thermalservice - NOT dumpsys battery
  [runner] no second driver alive, no gradle build running, host load under threshold
  [runner] expected window instrument present in pass 1 traces (app-embrace-start wrapper)
  [human ] app source untouched between cells; no commit/sync mid-campaign
  [human ] launch-index policy declared (settled discards launches 0-2)
  [human ] device screen/charging/airplane state left to the runner to save and restore
  [human ] compat patches reverted and the version pin restored when the run ends
"""

    data class Cell(val id: String, val version: String, val levels: Map<String, JsonElement>, val device: String, val group: String) {
        fun toJson(): JsonObject = JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "version" to JsonPrimitive(version),
                "levels" to JsonObject(levels),
                "device" to JsonPrimitive(device),
                "group" to JsonPrimitive(group),
            ),
        )
    }

    class PlanError(message: String) : IllegalArgumentException(message)

    /** The validations that must stop the plan before it runs: missing keys and unregistered devices. */
    fun validate(plan: JsonObject) {
        REQUIRED_KEYS.forEach { key ->
            if (key !in plan) throw PlanError("plan is missing required key: $key (see plan-example.json)")
        }
        val devices = plan.getValue("devices").jsonObject
        val primary = plan.getValue("primary_device").jsonPrimitive.content
        val needed = setOf(primary) + combos(plan).map { PyJson.str(it, "device", primary) }
        val missing = needed.filter { it !in devices }.sorted()
        if (missing.isNotEmpty()) {
            throw PlanError(
                "plan references device key(s) ${PyJson.reprList(missing)} with no entry in \"devices\" - add one " +
                    "per device with its serial and profile (see plan-example.json)",
            )
        }
    }

    /** Non-fatal warnings printed before the table. */
    fun warnings(plan: JsonObject): List<String> {
        val out = ArrayList<String>()
        plan.getValue("devices").jsonObject.forEach { (key, cfg) ->
            DEVICE_FIELDS.forEach { field ->
                if (!PyJson.truthy(cfg.jsonObject, field)) {
                    out.add(
                        "WARNING: device '$key' is missing '$field'. The profile travels with " +
                            "every result and is what makes runs comparable later - fill it in.",
                    )
                }
            }
        }
        val passes = passes(plan)
        if (passes < MIN_PASSES_FOR_SIGNIFICANCE) {
            out.add(
                "WARNING: $passes passes per arm cannot reach significance at all - the " +
                    "smallest attainable p-value is above 0.05. Add passes or state plainly that the " +
                    "cell is descriptive only.",
            )
        } else if (passes < SCOPED_PASSES) {
            out.add(
                "WARNING: ${passes}x${iterations(plan)} is weaker than the scoped 10x20 " +
                    "shape - precision and the p-value floor both improve with passes, not iterations. " +
                    "Say so in the report.",
            )
        }
        return out
    }

    fun cellId(version: String, levels: Map<String, JsonElement>, device: String): String {
        val tags = levels.entries.sortedBy { it.key }.map { "${it.key}=${scalar(it.value)}" }
        return "$device|$version|" + if (tags.isEmpty()) "reference" else tags.joinToString(",")
    }

    fun buildCells(plan: JsonObject): List<Cell> {
        val ref = plan.getValue("reference").jsonObject
        val dev = plan.getValue("primary_device").jsonPrimitive.content
        val cells = ArrayList<Cell>()
        plan.getValue("versions").jsonArray.forEach { v ->
            val version = v.jsonPrimitive.content
            cells.add(Cell(cellId(version, emptyMap(), dev), version, LinkedHashMap(ref), dev, "version-sweep"))
        }
        (plan["factor_levels"] as? JsonObject)?.forEach { (factor, levels) ->
            levels.jsonArray.forEach { level ->
                plan.getValue("anchors").jsonArray.forEach { v ->
                    val version = v.jsonPrimitive.content
                    val merged = LinkedHashMap(ref)
                    merged[factor] = level
                    cells.add(
                        Cell(cellId(version, mapOf(factor to level), dev), version, merged, dev, "factor:$factor=${scalar(level)}"),
                    )
                }
            }
        }
        combos(plan).forEach { combo ->
            val merged = LinkedHashMap(ref)
            val levels = combo.getValue("levels").jsonObject
            merged.putAll(levels)
            val device = PyJson.str(combo, "device", dev)
            val version = combo.getValue("version").jsonPrimitive.content
            cells.add(Cell(cellId(version, levels, device), version, merged, device, "combo:${PyJson.str(combo, "id", "None")}"))
        }
        return cells
    }

    fun interleave(cells: List<Cell>): List<Cell> {
        val refs = cells.filter { it.group == "version-sweep" }
        val byGroup = LinkedHashMap<String, ArrayDeque<Cell>>()
        cells.filter { it.group != "version-sweep" }.forEach { byGroup.getOrPut(it.group) { ArrayDeque() }.addLast(it) }
        val ordered = ArrayList(refs)
        val groups = byGroup.values.toList()
        while (groups.any { it.isNotEmpty() }) {
            groups.forEach { g -> g.removeFirstOrNull()?.let { ordered.add(it) } }
        }
        return ordered
    }

    fun estimateSeconds(cell: Cell, plan: JsonObject): Double {
        val tier = PyJson.strOrNull(PyJson.obj(plan.getValue("devices").jsonObject, cell.device), "tier")
        val perLaunch = LAUNCH_SECONDS[tier] ?: DEFAULT_LAUNCH_SECONDS
        val passes = passes(plan)
        val launches = passes * iterations(plan)
        var extra = 0.0
        if (cell.levels["install"]?.let { scalar(it) } in setOf("fresh", "updated")) extra += launches * INSTALL_S
        if (cell.levels["thermal"]?.let { scalar(it) } == "hot") extra += passes * HEAT_S
        return launches * perLaunch + passes * (PASS_OVERHEAD_S + COOL_GATE_S) + extra
    }

    data class Result(val text: String, val cells: List<Cell>)

    /** The report body: warnings, the per-cell estimate table with night breaks, the total, and the checklist. */
    fun report(plan: JsonObject): Result {
        validate(plan)
        val out = StringBuilder()
        warnings(plan).forEach { out.append(it).append('\n') }
        val cells = interleave(buildCells(plan))
        val nightBudget = (PyJson.double(plan, "night_budget_hours") ?: DEFAULT_NIGHT_HOURS) * SECONDS_PER_HOUR
        var night = 1
        var nightUsed = 0.0
        var total = 0.0
        out.append(
            "\nrun ${PyJson.str(plan, "run_id", "None")}: ${cells.size} cells, ${passes(plan)}x${iterations(plan)} each, " +
                "build=${PyJson.str(plan, "build_type", "benchmark")}, " +
                "night budget ${PyFormat.fixed(nightBudget / SECONDS_PER_HOUR, 0)} h\n\n",
        )
        out.append("${"#".padStart(W3)}  ${"cell".padEnd(W58)}${"device".padEnd(W8)}${"group".padEnd(W26)}${"est".padStart(W7)}\n")
        cells.forEachIndexed { idx, c ->
            val secs = estimateSeconds(c, plan)
            if (nightUsed + secs > nightBudget && nightUsed > 0) {
                out.append(
                    "     ${"-".repeat(W40)} night $night full (${PyFormat.fixed(nightUsed / SECONDS_PER_HOUR, 1)} h) ${"-".repeat(W12)}\n",
                )
                night++
                nightUsed = 0.0
            }
            nightUsed += secs
            total += secs
            out.append(
                "${(idx + 1).toString().padStart(W3)}  ${c.id.padEnd(W58)}${c.device.padEnd(W8)}${c.group.padEnd(W26)}" +
                    "${PyFormat.fixed(secs / SECONDS_PER_MINUTE, 0).padStart(W6)}m\n",
            )
        }
        out.append(
            "\ntotal estimate: ${PyFormat.fixed(total / SECONDS_PER_HOUR, 1)} h across $night night(s); " +
                "${cells.count { it.group == "version-sweep" }} version-sweep (reference) " +
                "cells run FIRST so factor deltas always have a baseline\n",
        )
        out.append(CHECKLIST).append('\n')
        return Result(out.toString(), cells)
    }

    fun emit(plan: JsonObject, cells: List<Cell>): JsonObject =
        JsonObject(mapOf("plan" to plan, "cells" to JsonArray(cells.map { it.toJson() })))

    private fun combos(plan: JsonObject): List<JsonObject> =
        (plan["combos"] as? JsonArray)?.map { it.jsonObject } ?: emptyList()

    private fun passes(plan: JsonObject): Int = plan.getValue("passes").jsonPrimitive.content.toDouble().toInt()

    private fun iterations(plan: JsonObject): Int = plan.getValue("iterations").jsonPrimitive.content.toDouble().toInt()

    /** Python `str()` of a level value as it appears in cell ids and group names. */
    private fun scalar(el: JsonElement): String = if (el is JsonPrimitive) el.content else PyJson.dumps(el)

    private const val MIN_PASSES_FOR_SIGNIFICANCE = 4
    private const val SCOPED_PASSES = 10
    private const val INSTALL_S = 12.0
    private const val HEAT_S = 600.0
    private const val DEFAULT_NIGHT_HOURS = 8.0
    private const val SECONDS_PER_HOUR = 3600.0
    private const val SECONDS_PER_MINUTE = 60.0
    private const val W3 = 3
    private const val W6 = 6
    private const val W7 = 7
    private const val W8 = 8
    private const val W12 = 12
    private const val W26 = 26
    private const val W40 = 40
    private const val W58 = 58
}
