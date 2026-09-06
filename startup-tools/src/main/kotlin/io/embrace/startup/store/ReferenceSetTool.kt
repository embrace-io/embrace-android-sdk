package io.embrace.startup.store

import io.embrace.startup.core.json.DeviceProfile
import io.embrace.startup.core.json.PyJson
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.device.DeviceProbe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * `reference_set.py`: declare and inspect the stable reference device set.
 *
 * Probes every attached device for its PROFILE (not its identity), assigns each a provisional key
 * (`<tier>-<letter>`) the operator renames and keeps forever, and freezes the measurement recipe with
 * `instrument` deliberately null so the first ingest fails loudly rather than defaulting to a span the
 * harness never emits. Re-probing flags profile DRIFT: an OS upgrade or a replaced handset must become
 * a new key.
 *
 * The document is handled as loose JSON here (not [io.embrace.startup.core.json.ReferenceSet]) because
 * this tool WRITES the `_comment` documentation arrays and the null instrument that the typed reader
 * tolerates but does not preserve.
 */
object ReferenceSetTool {

    val PROFILE_FIELDS: List<String> = listOf(
        "api_level",
        "release",
        "tier",
        "vendor",
        "soc_family",
        "clusters",
        "ram_class",
        "storage_class",
    )
    const val DEFAULT_COOL_GATE_C: Double = 32.0

    fun recipeDefault(): JsonObject = JsonObject(
        mapOf(
            "run_shape" to JsonObject(mapOf("passes" to JsonPrimitive(DEFAULT_PASSES), "iterations" to JsonPrimitive(DEFAULT_ITERATIONS))),
            "build_type" to JsonPrimitive("benchmark"),
            "compile_state" to JsonPrimitive("profile"),
            "instrument" to JsonNull,
            "_comment" to JsonPrimitive(
                "EVERY value above is provisional output of --probe, not just the device keys: " +
                    "review the recipe field by field before the first ingest. Changing any of these " +
                    "later starts a NEW comparable series; see references/store.md",
            ),
        ),
    )

    /** What the set cannot answer - a set chosen by convenience usually cannot separate what the operator most wants separated. */
    fun coverageWarnings(devices: JsonObject): List<String> {
        val profiles = devices.values.map { it.jsonObject }
        val apis = profiles.map { PyJson.strOrNull(PyJson.obj(it, "profile"), "api_level") }.toSet()
        val vendors = profiles.map { (PyJson.strOrNull(PyJson.obj(it, "profile"), "vendor") ?: "").lowercase() }.toSet()
        val tiers = profiles.map { PyJson.strOrNull(it, "tier") ?: PyJson.strOrNull(PyJson.obj(it, "profile"), "tier") }.toSet()
        val warnings = ArrayList<String>()
        if (devices.size < 2) warnings.add("single device: cannot separate SDK effects from device-class effects")
        if (apis.size < 2) {
            warnings.add("one ART/Android generation: compile-state and class-load findings may not transfer to other releases")
        }
        if (vendors.size < 2) {
            warnings.add(
                "one vendor: cannot separate OEM policy (install-time compilation, thermal governors, procfs readability) from silicon",
            )
        }
        if (tiers.none { it == "entry" || it == "entry-mid" }) {
            warnings.add(
                "no entry/low-RAM device: the outlier classes that hurt users most " +
                    "(memory pressure, GC competition) will be under-represented",
            )
        }
        if (profiles.any { (PyJson.double(PyJson.obj(it, "profile"), "api_level") ?: 0.0) < MIN_PROFILEABLE_API }) {
            warnings.add("a device below API 29 cannot be traced as a profileable non-debuggable target; its numbers are not comparable")
        }
        return warnings
    }

    /** `--probe --out`: provisional keys `<tier>-a`, `<tier>-b`, ... in serial order, plus the console lines. */
    fun declare(probed: Map<String, DeviceProfile>, declaredAt: String): Pair<JsonObject, List<String>> {
        val lines = ArrayList<String>()
        val devices = LinkedHashMap<String, JsonElement>()
        probed.entries.sortedBy { it.key }.forEachIndexed { i, (serial, profile) ->
            val tier = DeviceProbe.tierGuess(profile.ramClass ?: "unknown", profile.clusters ?: emptyList())
            val key = "$tier-${'a' + i}"
            devices[key] = JsonObject(
                mapOf(
                    "serial" to JsonPrimitive(serial),
                    "tier" to JsonPrimitive(tier),
                    "profile" to StartupJson.encodeToJsonElement(DeviceProfile.serializer(), profile),
                    "cool_gate_c" to JsonPrimitive(DEFAULT_COOL_GATE_C),
                    "retired" to JsonPrimitive(false),
                ),
            )
            lines.add(
                "$key: $serial api=${profile.apiLevel} vendor=${profile.vendor} " +
                    "soc=${profile.socFamily} ram=${profile.ramClass} tier~$tier",
            )
        }
        val doc = JsonObject(
            mapOf(
                "declared_at" to JsonPrimitive(declaredAt),
                "recipe" to recipeDefault(),
                "devices" to JsonObject(devices),
                "_comment" to JsonArray(
                    listOf(
                        "device_key is YOUR stable label - rename these to something meaningful and " +
                            "keep them forever; they are the axis every longitudinal report uses.",
                        "tier is a guess from RAM and cluster topology; confirm it by hand.",
                        "cool_gate_c is per device: a plugged-in device's warm idle floor can sit " +
                            "above a naive gate, so set it from an observed idle temperature.",
                    ).map { JsonPrimitive(it) },
                ),
            ),
        )
        coverageWarnings(JsonObject(devices)).forEach { lines.add("COVERAGE GAP: $it") }
        return doc to lines
    }

    /** `--probe --check`: drift per known device, new devices, and missing (unretired) ones. */
    fun check(doc: JsonObject, probed: Map<String, DeviceProfile>): List<String> {
        val lines = ArrayList<String>()
        val known = PyJson.obj(doc, "devices") ?: JsonObject(emptyMap())
        val bySerial = known.entries.associate { (key, cfg) -> PyJson.strOrNull(cfg.jsonObject, "serial") to (key to cfg.jsonObject) }
        probed.forEach { (serial, profile) ->
            val entry = bySerial[serial]
            if (entry == null) {
                lines.add("NEW device attached (not in the reference set): $serial -> ${profileRepr(profile)}")
                return@forEach
            }
            val (key, cfg) = entry
            val knownProfile = PyJson.obj(cfg, "profile") ?: JsonObject(emptyMap())
            val now = StartupJson.encodeToJsonElement(DeviceProfile.serializer(), profile).jsonObject
            val drift = PROFILE_FIELDS.filter { f ->
                f in knownProfile && PyJson.dumps(knownProfile.getValue(f)) != PyJson.dumps(now[f] ?: JsonNull)
            }
            if (drift.isNotEmpty()) {
                lines.add("PROFILE DRIFT on '$key' ($serial):")
                drift.forEach { f -> lines.add("    $f: ${repr(knownProfile.getValue(f))} -> ${repr(now[f] ?: JsonNull)}") }
                lines.add(
                    "    -> treat this as a NEW device_key and re-establish its baseline; do " +
                        "NOT carry the old baseline across (see references/store.md).",
                )
            } else {
                lines.add("ok '$key' ($serial): profile unchanged")
            }
        }
        known.forEach { (key, cfg) ->
            val serial = PyJson.strOrNull(cfg.jsonObject, "serial")
            if (serial !in probed && !PyJson.bool(cfg.jsonObject, "retired", false)) {
                lines.add(
                    "MISSING '$key' ($serial): attach it or mark it retired - a " +
                        "silent gap in the series is where slow regressions hide",
                )
            }
        }
        return lines
    }

    /** Python `repr` of a JSON scalar or container, for the drift lines. */
    private fun repr(el: JsonElement): String = when {
        el is JsonNull -> "None"
        el is JsonPrimitive && el.isString -> PyJson.repr(el.content)
        el is JsonPrimitive -> el.content
        el is JsonArray -> el.joinToString(", ", "[", "]") { repr(it) }
        else -> PyJson.dumps(el)
    }

    private fun profileRepr(profile: DeviceProfile): String {
        val obj = StartupJson.encodeToJsonElement(DeviceProfile.serializer(), profile).jsonObject
        return obj.entries.joinToString(", ", "{", "}") { (k, v) -> "${PyJson.repr(k)}: ${repr(v)}" }
    }

    private const val DEFAULT_PASSES = 10
    private const val DEFAULT_ITERATIONS = 20
    private const val MIN_PROFILEABLE_API = 29.0
}
