package io.embrace.analysis.records.store

import io.embrace.analysis.common.io.Zips
import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.common.json.StartupJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

/**
 * What a committed campaign archive may hold, and the one-time repair of archives written before the rule.
 *
 * An archive is EVIDENCE: what a statement is computed from. That is the per-pass datasets (`passN.json`,
 * `passN-factors.json`, `passN-cohorts.json`), the harness's own per-iteration output
 * (`passN/…benchmarkData.json`), the driver's log (`campaign.log`, or `cell.log` for a matrix cell), and
 * the provenance files - with the device named by its key. Everything else a run directory holds is RAW:
 * device output the datasets were reduced from (the logcat captures), rendered reports re-derivable from
 * the datasets in a second, build logs, and the serial that names a handset on one machine. Raw members move to the local root
 * (`<local>/data/campaigns/<run-id>/`), where they remain the only copy; serials are replaced by the
 * device key, with the original provenance kept beside the raw members.
 *
 * [cull] is idempotent: an archive already in shape is left byte-identical, so a rerun costs git nothing.
 */
object ArchiveHygiene {

    data class Report(
        val archive: Path,
        /** Members moved to the local root. */
        val culled: List<String>,
        /** Members rewritten with serials replaced or removed. */
        val scrubbed: List<String>,
        /** Members whose serial no local map entry could name (the serial was still removed). */
        val unresolved: List<String>,
    ) {
        val changed: Boolean get() = culled.isNotEmpty() || scrubbed.isNotEmpty()
    }

    /** Whether an archive member (entry name, possibly nested) belongs in the committed evidence. */
    fun isEvidence(name: String): Boolean {
        val entry = name.replace('\\', '/')
        val base = entry.substringAfterLast('/')
        val depth = entry.count { it == '/' }
        return when {
            depth == 0 -> base in PROVENANCE || base in RUN_LOGS || DATASET.matches(base)
            depth == 1 -> PASS_DIR.matches(entry.substringBefore('/')) && HARNESS_DATA.matches(base)
            else -> false
        }
    }

    /** The evidence files under a run directory, sorted by their path relative to it. */
    fun evidenceFiles(runDir: Path): List<Path> =
        Files.walk(runDir).use { walk ->
            walk.filter { Files.isRegularFile(it) && isEvidence(runDir.relativize(it).toString()) }.toList()
        }.sortedBy { runDir.relativize(it).toString() }

    /**
     * The provenance document with its `serial` replaced by `device_key` (kept in the serial's position).
     * Returns the same instance when there is no serial; [resolved] is false when the key is unknown, in
     * which case the serial is still dropped.
     */
    fun scrubProvenance(doc: JsonObject, serials: DeviceSerials.Serials): Pair<JsonObject, Boolean> {
        val serial = PyJson.strOrNull(doc, "serial") ?: return doc to true
        val key = PyJson.strOrNull(doc, "device_key") ?: serials.keyFor(serial)
        val out = LinkedHashMap<String, JsonElement>()
        doc.forEach { (k, v) ->
            if (k == "serial") {
                out["device_key"] = key?.let { JsonPrimitive(it) } ?: JsonNull
            } else if (k != "device_key") {
                out[k] = v
            }
        }
        return JsonObject(out) to (key != null)
    }

    /** Any JSON with every `serial` field removed from `devices` maps (reference sets, plan device maps). */
    fun scrubDeviceMaps(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(
            el.entries.associate { (k, v) ->
                if (k == "devices" && v is JsonObject) {
                    k to JsonObject(v.mapValues { (_, cfg) -> stripSerial(cfg) })
                } else {
                    k to scrubDeviceMaps(v)
                }
            },
        )
        is JsonArray -> JsonArray(el.map { scrubDeviceMaps(it) })
        else -> el
    }

    /** [text] with every known serial replaced by `<device-key>`; null when none occurs. */
    fun scrubText(text: String, serials: DeviceSerials.Serials): String? {
        var out = text
        serials.byKey.forEach { (key, serial) -> out = out.replace(serial, "<$key>") }
        return out.takeIf { it != text }
    }

    /**
     * Repair one campaign archive in place: raw members move to `<localDir>/<run-id>/`, provenance names
     * the device by key, known serials vanish from text members. Every member that is rewritten is copied
     * to the local directory as it was first, so nothing the archive ever held is lost to the machine.
     */
    fun cull(archive: Path, localDir: Path, serials: DeviceSerials.Serials): Report {
        val runId = archive.fileName.toString().removeSuffix(".zip")
        val entries = Zips.readAll(archive)
        val kept = LinkedHashMap<String, ByteArray>()
        val culled = ArrayList<String>()
        val scrubbed = ArrayList<String>()
        val unresolved = ArrayList<String>()
        val rawDir = localDir.resolve(runId)
        entries.forEach { (name, blob) ->
            if (!isEvidence(name)) {
                writeLocal(rawDir, name, blob)
                culled.add(name)
                return@forEach
            }
            val base = name.substringAfterLast('/')
            val rewritten: ByteArray? = when {
                base in PROVENANCE -> {
                    val doc = StartupJson.parseToJsonElement(blob.toString(Charsets.UTF_8)) as JsonObject
                    val (out, resolved) = scrubProvenance(doc, serials)
                    if (out === doc) {
                        null
                    } else {
                        writeLocal(rawDir, name, blob)
                        if (!resolved) unresolved.add(name)
                        StartupJson.encodeToString(JsonObject.serializer(), out).toByteArray()
                    }
                }
                base.endsWith(".json") -> scrubJson(blob)
                else -> scrubText(blob.toString(Charsets.UTF_8), serials)?.toByteArray()?.also { writeLocal(rawDir, name, blob) }
            }
            if (rewritten != null) scrubbed.add(name)
            kept[name] = rewritten ?: blob
        }
        val report = Report(archive, culled, scrubbed, unresolved)
        if (report.changed) Zips.write(archive, kept)
        return report
    }

    /** Scrub serials from a non-campaign archive (an experiment) without culling: device maps and known serials in text. */
    fun scrub(archive: Path, serials: DeviceSerials.Serials): Report {
        val entries = Zips.readAll(archive)
        val kept = LinkedHashMap<String, ByteArray>()
        val scrubbed = ArrayList<String>()
        entries.forEach { (name, blob) ->
            val rewritten = if (name.endsWith(".json")) {
                scrubJson(blob) ?: scrubText(blob.toString(Charsets.UTF_8), serials)?.toByteArray()
            } else {
                scrubText(blob.toString(Charsets.UTF_8), serials)?.toByteArray()
            }
            if (rewritten != null) scrubbed.add(name)
            kept[name] = rewritten ?: blob
        }
        val report = Report(archive, emptyList(), scrubbed, emptyList())
        if (report.changed) Zips.write(archive, kept)
        return report
    }

    private fun scrubJson(blob: ByteArray): ByteArray? {
        val text = blob.toString(Charsets.UTF_8)
        if ("\"serial\"" !in text) return null
        val el = runCatching { StartupJson.parseToJsonElement(text) }.getOrNull() ?: return null
        val out = scrubDeviceMaps(el)
        if (out == el) return null
        return (PyJson.dumpsPretty(out) + "\n").toByteArray()
    }

    private fun stripSerial(cfg: JsonElement): JsonElement =
        if (cfg is JsonObject && "serial" in cfg) JsonObject(cfg.filterKeys { it != "serial" }) else cfg

    private fun writeLocal(rawDir: Path, name: String, blob: ByteArray) {
        val target = rawDir.resolve(name.replace('\\', '/'))
        require(!name.startsWith("/") && name.split('/').none { it == ".." }) { "refusing entry '$name'" }
        target.parent?.let { Files.createDirectories(it) }
        Files.write(target, blob)
    }

    /** The driver's own log of the run: the fleet campaign's, and the matrix cell's (which records the invariants that passed). */
    private val RUN_LOGS = setOf("campaign.log", "cell.log")
    private val PROVENANCE = setOf("run-metadata.json", "cell-state.json")
    private val DATASET = Regex("pass[0-9]+(-[a-z-]+)?\\.json")
    private val PASS_DIR = Regex("pass[0-9]+")
    private val HARNESS_DATA = Regex(".*benchmarkData\\.json")
}
