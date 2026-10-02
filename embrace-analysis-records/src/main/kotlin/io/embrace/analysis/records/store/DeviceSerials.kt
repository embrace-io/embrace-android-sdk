package io.embrace.analysis.records.store

import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.common.repo.RepoRoot
import io.embrace.analysis.records.ReferenceSet
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * The machine-local map from device key to serial, `<local>/data/device-serials.json`.
 *
 * A serial is what a device is called on THIS machine's adb; a device key is what it is called in every
 * committed record. The two are joined only here, so the committed root can name devices by key
 * everywhere - the reference set, provenance, logs - and still let the tooling on the machine that owns
 * the handsets find them. The serial plays the role an app id plays for the production tool: the pinned
 * dimension that removes noise, and the identifying value that keeps a file out of the repository.
 *
 * Readers do not consult this map directly: [hydrate] fills the serials back into an in-memory reference
 * set, so `ingest`, `maxims score` and the reference-set checks keep working on serial-bearing provenance
 * from runs recorded before the split, and on live devices now.
 */
object DeviceSerials {

    const val FILE_NAME: String = "device-serials.json"

    /** Device key to serial. */
    data class Serials(val byKey: Map<String, String>) {
        fun serialFor(key: String): String? = byKey[key]

        fun keyFor(serial: String): String? = byKey.entries.firstOrNull { it.value == serial }?.key

        fun isEmpty(): Boolean = byKey.isEmpty()

        companion object {
            val EMPTY: Serials = Serials(emptyMap())
        }
    }

    fun file(repo: Path = RepoRoot.locate()): Path = RecordsRoot.localData(repo).resolve(FILE_NAME)

    /** The map at [file], empty when the file does not exist. */
    fun load(file: Path): Serials {
        if (!Files.isRegularFile(file)) return Serials.EMPTY
        val doc = StartupJson.parseToJsonElement(Files.readString(file)).jsonObject
        val devices = PyJson.obj(doc, "devices") ?: JsonObject(emptyMap())
        return Serials(devices.mapValues { (_, v) -> (v as JsonPrimitive).content })
    }

    fun save(serials: Serials, file: Path) {
        file.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        val doc = JsonObject(
            mapOf(
                "_comment" to JsonPrimitive(
                    "device key -> adb serial for the handsets attached to THIS machine. Never committed: " +
                        "every committed record names a device by its key only.",
                ),
                "devices" to JsonObject(serials.byKey.toSortedMap().mapValues { (_, s) -> JsonPrimitive(s) }),
            ),
        )
        Files.writeString(file, PyJson.dumpsPretty(doc) + "\n")
    }

    /** [local] with the serials a legacy reference-set document still carries filled in underneath it. */
    fun merge(local: Serials, ref: JsonObject?): Serials {
        val merged = LinkedHashMap<String, String>()
        PyJson.obj(ref, "devices")?.forEach { (key, cfg) ->
            PyJson.strOrNull(cfg.jsonObject, "serial")?.let { merged[key] = it }
        }
        merged.putAll(local.byKey)
        return Serials(merged)
    }

    /** The typed reference set with every device's serial taken from [serials] where the file carries none. */
    fun hydrate(ref: ReferenceSet, serials: Serials): ReferenceSet =
        ref.copy(
            devices = ref.devices.mapValues { (key, device) ->
                if (device.serial != null) device else device.copy(serial = serials.serialFor(key))
            },
        )

    /** The loose-JSON reference set with every device's serial taken from [serials] where the document carries none. */
    fun hydrate(doc: JsonObject, serials: Serials): JsonObject {
        val devices = PyJson.obj(doc, "devices") ?: return doc
        val filled = devices.mapValues { (key, cfg) ->
            val obj = cfg.jsonObject
            val serial = PyJson.strOrNull(obj, "serial") ?: serials.serialFor(key) ?: return@mapValues cfg
            JsonObject(obj + ("serial" to JsonPrimitive(serial)))
        }
        return JsonObject(doc + ("devices" to JsonObject(filled)))
    }

    /**
     * Split a reference-set document that carries serials into the committed form (no serials) and the
     * local map. A document already without serials splits into itself and an empty map.
     */
    fun split(doc: JsonObject): Pair<JsonObject, Serials> {
        val devices = PyJson.obj(doc, "devices") ?: return doc to Serials.EMPTY
        val serials = LinkedHashMap<String, String>()
        val stripped = devices.mapValues { (key, cfg) ->
            val obj = cfg.jsonObject
            PyJson.strOrNull(obj, "serial")?.let { serials[key] = it }
            JsonObject(obj.filterKeys { it != "serial" }) as JsonElement
        }
        return JsonObject(doc + ("devices" to JsonObject(stripped))) to Serials(serials)
    }
}
