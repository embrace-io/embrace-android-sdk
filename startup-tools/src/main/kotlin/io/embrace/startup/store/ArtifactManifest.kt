package io.embrace.startup.store

import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.repo.RepoRoot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * The living-docs drift manifest from `_shared/artifact_sync.py`: the digest of every doc at the
 * moment it was published FROM HERE, so "have I changed this since?" has an answer that does not
 * depend on memory. It lives in the committed records root beside the document sources it
 * describes. States: UNKNOWN (never recorded - fetch the published page and reconcile before
 * touching it), CLEAN (identical to the last publish from here - the published copy is the truth,
 * another session may still have moved it), DIRTY (changed locally - merge, never overwrite from
 * published). One audit found drift in both directions; the dangerous case is the edit
 * that looks successful.
 */
class ArtifactManifest(repo: Path) {

    val file: Path = RepoRoot.records(repo).resolve("artifact-manifest.json")

    fun load(): Map<String, JsonObject> {
        if (!Files.exists(file)) return emptyMap()
        return runCatching {
            StartupJson.parseToJsonElement(Files.readString(file)).jsonObject.mapValues { it.value.jsonObject }
        }
            .getOrDefault(emptyMap())
    }

    /** Exit code and the Python's wording: 2 missing, 1 unknown, 0 clean or dirty. */
    fun check(path: Path): Pair<Int, String> {
        val entry = load()[path.fileName.toString()]
        if (!Files.exists(path)) return 2 to "MISSING: $path does not exist"
        val name = path.fileName
        if (entry == null) {
            return 1 to "UNKNOWN  $name\n" +
                "  Never recorded as published from here. Before editing, fetch the published page\n" +
                "  and reconcile - the published copy may contain changes this file has never seen."
        }
        val url = entry["url"]?.jsonPrimitive?.content
        val at = entry["published_at"]?.jsonPrimitive?.content
        if (digest(path) == entry["sha"]?.jsonPrimitive?.content) {
            return 0 to "CLEAN    $name\n  url: $url\n" +
                "  Identical to the last publish from here ($at).\n" +
                "  The PUBLISHED copy is the truth. Another session may have changed it since, so\n" +
                "  fetch it before any substantive edit."
        }
        return 0 to "DIRTY    $name\n  url: $url\n" +
            "  Changed locally since the last publish ($at).\n" +
            "  Your local edits are real - do NOT replace this file from the published copy.\n" +
            "  If the publish tool demands a read, MERGE rather than overwrite."
    }

    fun record(path: Path, url: String, stamp: String): String {
        val data = LinkedHashMap(load())
        val sha = digest(path)
        data[path.fileName.toString()] = JsonObject(
            mapOf(
                "path" to JsonPrimitive(path.toAbsolutePath().toString()),
                "url" to JsonPrimitive(url),
                "sha" to JsonPrimitive(sha),
                "published_at" to JsonPrimitive(stamp),
            ),
        )
        file.parent?.let { Files.createDirectories(it) }
        Files.writeString(file, PRETTY.encodeToString(JsonObject.serializer(), JsonObject(data.toSortedMap())) + "\n")
        return "recorded ${path.fileName} -> $url ($sha)"
    }

    fun list(): String {
        val data = load()
        if (data.isEmpty()) return "no artifacts recorded yet"
        return data.toSortedMap().entries.joinToString("\n") { (name, entry) ->
            val p = Path.of(entry["path"]?.jsonPrimitive?.content ?: "")
            val state = when {
                !Files.exists(p) -> "MISSING"
                digest(p) == entry["sha"]?.jsonPrimitive?.content -> "clean"
                else -> "DIRTY"
            }
            "${state.padEnd(W8)}${name.padEnd(W44)}${(entry["published_at"]?.jsonPrimitive?.content ?: "").padEnd(W20)}" +
                (entry["url"]?.jsonPrimitive?.content ?: "")
        }
    }

    companion object {
        /** sha256 of the file's bytes, first 16 hex chars - the Python's digest. */
        fun digest(path: Path): String =
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") {
                "%02x".format(it)
            }.take(SHA_CHARS)

        private const val SHA_CHARS = 16
        private const val W8 = 8
        private const val W20 = 20
        private const val W44 = 44
        private val PRETTY = Json(StartupJson) { prettyPrint = true }
    }
}
