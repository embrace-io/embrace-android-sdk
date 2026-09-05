package io.embrace.startup.store

import io.embrace.startup.analysis.Maxims
import io.embrace.startup.core.json.PyJson
import io.embrace.startup.core.json.StartupJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path

/**
 * The maxims ledger (`_shared/records/maxims/ledger.json`): every campaign's verdict against every maxim, tallied
 * per device, with the contradictions kept beside the cell that produced them, plus the candidates.
 *
 * The ledger is kept as the JSON tree the Python writes rather than a typed schema, so a file written by
 * either toolchain loads in the other unchanged: keys the Python added later survive a round trip, number
 * literals are re-emitted verbatim, and [save] writes sorted keys at a two-space indent exactly as
 * `json.dumps(led, indent=2, sort_keys=True)` does. Shape:
 *
 * ```
 * {source, runs, updated_at,
 *  maxims: {id: {statement, scope, confirmed, contradicted, thin, undetected,
 *                devices: {key: {confirmed, contradicted, thin, undetected}},
 *                contradictions: [{run, device, cell, observed}]}},
 *  candidates: {name: {label, cells, max_lift, last_seen}}}
 * ```
 */
object MaximsLedger {

    fun empty(): JsonObject = JsonObject(
        mapOf(
            "source" to JsonPrimitive(Maxims.SOURCE),
            "runs" to JsonPrimitive(0),
            "updated_at" to JsonPrimitive(""),
            "maxims" to JsonObject(emptyMap()),
            "candidates" to JsonObject(emptyMap()),
        ),
    )

    fun load(path: Path): JsonObject {
        if (!Files.exists(path)) {
            return empty()
        }
        val led = StartupJson.parseToJsonElement(Files.readString(path)).jsonObject.toMutableMap()
        led.putIfAbsent("source", JsonPrimitive(Maxims.SOURCE))
        led.putIfAbsent("maxims", JsonObject(emptyMap()))
        led.putIfAbsent("candidates", JsonObject(emptyMap()))
        return JsonObject(led)
    }

    /** `json.dumps(led, indent=2, sort_keys=True) + "\n"`. */
    fun dumps(led: JsonObject): String = PyJson.dumpsPretty(led) + "\n"

    fun save(led: JsonObject, path: Path) {
        path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.writeString(path, dumps(led))
    }

    /** The `runs` counter as the file carries it. */
    fun runs(led: JsonObject): String = PyJson.str(led, "runs", "0")

    /** One campaign's verdicts and candidates tallied in; an `n/a` verdict leaves no trace on the device. */
    fun record(
        led: JsonObject,
        runId: String,
        cell: Maxims.Cell,
        verdicts: List<Pair<Maxims.Maxim, Maxims.Verdict>>,
        cands: List<Pair<String, Double>>,
        now: String,
    ): JsonObject {
        val root = led.toMutableMap()
        root["runs"] = JsonPrimitive(int(led, "runs") + 1)
        root["updated_at"] = JsonPrimitive(now)
        val maxims = objOf(root["maxims"]).toMutableMap()
        verdicts.forEach { (m, v) ->
            val t = (maxims[m.id] as? JsonObject ?: newTally(m)).toMutableMap()
            t["statement"] = JsonPrimitive(m.statement)
            t["scope"] = JsonPrimitive(m.scope)
            if (v.status in COUNTED) {
                val devices = objOf(t["devices"]).toMutableMap()
                val d = (devices[cell.device] as? JsonObject ?: zeroCounts()).toMutableMap()
                t[v.status] = JsonPrimitive(int(JsonObject(t), v.status) + 1)
                d[v.status] = JsonPrimitive(int(JsonObject(d), v.status) + 1)
                devices[cell.device] = JsonObject(d)
                t["devices"] = JsonObject(devices)
            }
            if (v.status == Maxims.CONTRADICTED) {
                val contradiction = JsonObject(
                    mapOf(
                        "run" to JsonPrimitive(runId),
                        "device" to JsonPrimitive(cell.device),
                        "cell" to JsonPrimitive(cell.label),
                        "observed" to JsonPrimitive(v.observed),
                    ),
                )
                val kept = ((t["contradictions"] as? JsonArray)?.toList() ?: emptyList()) + contradiction
                t["contradictions"] = JsonArray(kept.takeLast(Maxims.MAX_CONTRADICTIONS))
            }
            maxims[m.id] = JsonObject(t)
        }
        root["maxims"] = JsonObject(maxims)
        val candidates = objOf(root["candidates"]).toMutableMap()
        cands.forEach { (name, lift) ->
            val cnd = (candidates[name] as? JsonObject ?: newCandidate(name)).toMutableMap()
            cnd["cells"] = JsonPrimitive(int(JsonObject(cnd), "cells") + 1)
            val rounded = round4(lift)
            val existing = cnd["max_lift"] as? JsonPrimitive
            val existingValue = existing?.content?.toDoubleOrNull() ?: 0.0
            // Python `max(a, b)` keeps the first on a tie, so the stored literal survives.
            if (existing == null || rounded.toDouble() > existingValue) {
                cnd["max_lift"] = JsonPrimitive(rounded)
            }
            cnd["last_seen"] = JsonPrimitive(runId)
            candidates[name] = JsonObject(cnd)
        }
        root["candidates"] = JsonObject(candidates)
        return JsonObject(root)
    }

    /** A device's stance on a maxim from its counts. */
    fun stance(d: JsonObject): String {
        val confirmed = int(d, "confirmed")
        val contradicted = int(d, "contradicted")
        val undetected = int(d, "undetected")
        return when {
            confirmed == 0 && contradicted == 0 && undetected > 0 -> "undetected"
            confirmed == 0 && contradicted == 0 -> "untested"
            confirmed >= 2 * contradicted -> "holds"
            contradicted >= 2 * confirmed -> "fails"
            else -> "mixed"
        }
    }

    fun statusOf(scope: String, tally: JsonObject?): String {
        val stances = stancesOf(tally) ?: return "untested"
        val holds = stances.count { it == "holds" }
        val fails = stances.count { it == "fails" }
        val mixed = stances.count { it == "mixed" }
        return when {
            stances.all { it == "untested" } -> "untested"
            scope == Maxims.DEVICE_SPECIFIC -> "accepted (device-specific)"
            fails > 0 && fails >= holds -> "refuted"
            fails > 0 || mixed > 0 -> "under review"
            else -> "accepted"
        }
    }

    fun devicesLine(tally: JsonObject?): String {
        val stances = stancesOf(tally) ?: return "no device has tested it"
        val tested = stances.count { it != "untested" }
        if (tested == 0) {
            return "no device has tested it"
        }
        var out = "holds on ${stances.count { it == "holds" }} of $tested devices tested"
        val rest = listOf("fails", "mixed", "undetected")
            .map { k -> k to stances.count { it == k } }
            .filter { it.second != 0 }
            .map { "${it.second} ${it.first}" }
        if (rest.isNotEmpty()) {
            out += " (" + rest.joinToString(", ") + ")"
        }
        return out
    }

    /** An integer field read as Python would (`d[key]`), a missing or non-numeric one as 0. */
    fun int(obj: JsonObject?, key: String): Int {
        val content = (obj?.get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
        return content?.toIntOrNull() ?: content?.toDoubleOrNull()?.toInt() ?: 0
    }

    /** One stance per device, or null when `not tally or not tally.get("devices")`. */
    private fun stancesOf(tally: JsonObject?): List<String>? {
        val devices = PyJson.obj(tally, "devices")
        if (tally.isNullOrEmpty() || devices.isNullOrEmpty()) {
            return null
        }
        return devices.values.map { stance(it.jsonObject) }
    }

    private fun objOf(el: JsonElement?): JsonObject = el as? JsonObject ?: JsonObject(emptyMap())

    private fun zeroCounts(): JsonObject = JsonObject(
        mapOf(
            "confirmed" to JsonPrimitive(0),
            "contradicted" to JsonPrimitive(0),
            "thin" to JsonPrimitive(0),
            "undetected" to JsonPrimitive(0),
        ),
    )

    private fun newTally(m: Maxims.Maxim): JsonObject = JsonObject(
        zeroCounts() + mapOf(
            "statement" to JsonPrimitive(m.statement),
            "scope" to JsonPrimitive(m.scope),
            "devices" to JsonObject(emptyMap()),
            "contradictions" to JsonArray(emptyList()),
        ),
    )

    private fun newCandidate(name: String): JsonObject = JsonObject(
        mapOf(
            "label" to JsonPrimitive(name),
            "cells" to JsonPrimitive(0),
            "max_lift" to JsonPrimitive(0.0),
            "last_seen" to JsonPrimitive(""),
        ),
    )

    /**
     * Python `round(lift, 4)` as `repr` prints it: half-even on the exact binary value, trailing zeros
     * dropped, at least one decimal kept (`2.0`, `2.25`, `0.0001`).
     */
    private fun round4(x: Double): BigDecimal {
        val d = BigDecimal(x).setScale(LIFT_DP, RoundingMode.HALF_EVEN).stripTrailingZeros()
        return if (d.scale() <= 0) d.setScale(1) else d
    }

    private val COUNTED = setOf(Maxims.CONFIRMED, Maxims.CONTRADICTED, Maxims.THIN, Maxims.UNDETECTED)
    private const val LIFT_DP = 4
}
