package io.embrace.startup.store

import io.embrace.startup.core.json.PyJson
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.text.PyFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * `submit_run.py`: turn a locally ingested run into a corpus submission - collect provenance, REDACT,
 * validate, append.
 *
 * Two design choices matter more than the code: the record is built field by field from an explicit
 * ALLOWLIST so a field nobody considered cannot leak by default (raw traces are never submitted - they
 * carry every running process name), and admissibility failures stop the submission instead of being
 * smoothed over. The serial is used only to derive a salted local unit token and is never stored.
 */
object Submit {

    const val SCHEMA_VERSION: Int = 1

    data class Outcome(val record: JsonObject?, val problems: List<String>)

    /** The LAST store line whose run_id matches, as the Python's loop left it. */
    fun findLocal(store: Path, runId: String): JsonObject? {
        var found: JsonObject? = null
        Files.readAllLines(store).forEach { line ->
            if (line.isBlank()) return@forEach
            val rec = StartupJson.parseToJsonElement(line).jsonObject
            if (PyJson.strOrNull(rec, "run_id") == runId) found = rec
        }
        return found
    }

    fun admissibilityProblems(local: JsonObject, lossyTolerancePct: Double, device: JsonObject?, serialGiven: Boolean): List<String> {
        val problems = ArrayList<String>()
        problems.addAll(healthProblems(PyJson.obj(local, "trace_health"), lossyTolerancePct))
        val sdk = PyJson.strOrNull(local, "sdk_version")
        if (!Ingest.isPublished(sdk)) {
            problems.add(
                "sdk_version ${if (sdk == null) "None" else PyJson.repr(sdk)} is not a published artifact - " +
                    "working-tree builds are not reproducible by others and are inadmissible",
            )
        }
        if (!PyJson.truthy(PyJson.obj(local, "derived"), "n")) problems.add("no derived statistics in the local record")
        val recipe = PyJson.obj(local, "recipe")
        listOf("build_type", "compile_state", "instrument").forEach { field ->
            if (!PyJson.truthy(recipe, field)) problems.add("recipe.$field missing - the cell key would be incomplete")
        }
        if (serialGiven && PyJson.strOrNull(device, "os_build").isNullOrEmpty()) {
            problems.add(
                "could not read the OS build fingerprint - same api_level is NOT the same " +
                    "software, so the record would not be groupable",
            )
        }
        return problems
    }

    /**
     * Buffer-level loss can have removed the window the submitted distribution is made of, so it gates
     * admissibility. Event-parse errors do not - surviving durations are correct - but they invalidate
     * the signal inventory, which another contributor would read as "this unit does not emit that".
     */
    private fun healthProblems(health: JsonObject?, lossyTolerancePct: Double): List<String> {
        val problems = ArrayList<String>()
        val tracesN = PyJson.double(health, "traces")?.toInt() ?: 0
        val bufferLoss = PyJson.double(health, "buffer_loss")?.toInt() ?: 0
        if (tracesN > 0 && bufferLoss > 0) {
            val share = PERCENT * bufferLoss / tracesN
            if (share > lossyTolerancePct) {
                problems.add(
                    "$bufferLoss/$tracesN traces (${PyFormat.fixed(share, 0)}%) lost written data, " +
                        "above the ${PyFormat.fixed(lossyTolerancePct, 0)}% tolerance - the submitted " +
                        "distribution may be built on evicted windows",
                )
            }
        }
        if (health != null && health.isNotEmpty() && !PyJson.bool(health, "signals_from_clean_trace", true)) {
            problems.add(
                "the signal inventory did not come from a clean trace (event-parse " +
                    "errors), so signals_present cannot be distinguished from a genuine " +
                    "absence - re-capture, or submit with signals_present omitted",
            )
        }
        return problems
    }

    /** The allowlisted record plus the device provenance fields; null record when inadmissible. */
    fun build(
        local: JsonObject,
        contributor: String,
        runId: String,
        submittedAt: String,
        device: JsonObject?,
        lossyTolerancePct: Double,
    ): Outcome {
        val problems = admissibilityProblems(local, lossyTolerancePct, device, serialGiven = device != null)
        if (problems.isNotEmpty()) return Outcome(null, problems)
        val fields = LinkedHashMap<String, JsonElement>()
        fields["schema_version"] = JsonPrimitive(SCHEMA_VERSION)
        fields["submission_id"] = JsonPrimitive("$contributor-$runId")
        fields["submitted_at"] = JsonPrimitive(submittedAt)
        fields["contributor"] = JsonPrimitive(contributor)
        fields["sdk_version"] = local["sdk_version"] ?: JsonNull
        fields["app_build_id"] = local["app_build_id"] ?: JsonNull
        fields["recipe"] = PyJson.obj(local, "recipe") ?: JsonObject(emptyMap())
        fields["conditions"] = PyJson.obj(local, "conditions") ?: JsonObject(emptyMap())
        fields["derived"] = PyJson.obj(local, "derived") ?: JsonObject(emptyMap())
        fields["windows_ms"] = PyJson.arr(local, "windows_ms") ?: JsonArray(emptyList())
        fields["signals_present"] = PyJson.arr(local, "signals_present") ?: JsonArray(emptyList())
        fields["trace_health"] = PyJson.obj(local, "trace_health") ?: JsonObject(emptyMap())
        fields["notes"] = local["notes"]?.takeIf { it !is JsonNull } ?: JsonPrimitive("")
        device?.forEach { (k, v) -> fields[k] = v }
        return Outcome(JsonObject(fields), emptyList())
    }

    /** The console preview: the record without its window values, then the count and the redaction reminder. */
    fun preview(record: JsonObject): String {
        val shown = JsonObject(record.filterKeys { it != "windows_ms" })
        val windows = (record["windows_ms"] as? JsonArray)?.size ?: 0
        return PRETTY.encodeToString(JsonObject.serializer(), shown) + "\n" +
            "(+ $windows per-iteration window values)\n" +
            "\nredaction check: no serial, no package names, no paths, no raw traces in the above."
    }

    fun append(corpus: Path, record: JsonObject) {
        corpus.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.writeString(
            corpus,
            StartupJson.encodeToString(JsonObject.serializer(), record) + "\n",
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        )
    }

    private val PRETTY = kotlinx.serialization.json.Json(StartupJson) { prettyPrint = true }
    private const val PERCENT = 100.0
}
