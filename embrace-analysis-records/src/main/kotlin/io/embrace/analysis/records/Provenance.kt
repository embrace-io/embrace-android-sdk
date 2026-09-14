package io.embrace.analysis.records

import io.embrace.analysis.common.json.StartupJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * A run's provenance file: what produced it and on which device. Two producers write one, and a
 * consumer should accept either without knowing which ran - `cell-state.json` from a matrix cell,
 * `run-metadata.json` from a fleet campaign. The cell state wins when both are present because it
 * is the more specific of the two.
 */
object Provenance {

    /**
     * The first provenance file found under [runDir], with its name, or null when the run has none. One
     * walk of the directory serves both candidates; when a name appears more than once (a cell directory
     * holding pass directories, say) the lexically first path wins, so the choice is stable.
     */
    fun load(runDir: Path): Pair<JsonObject, String>? {
        val candidates = Files.walk(runDir).use { s ->
            s.filter { Files.isRegularFile(it) && it.fileName.toString() in FILE_NAMES }.toList()
        }.sorted()
        FILE_NAMES.forEach { name ->
            candidates.firstOrNull { it.fileName.toString() == name }?.let {
                return StartupJson.parseToJsonElement(Files.readString(it)).jsonObject to name
            }
        }
        return null
    }

    private val FILE_NAMES = listOf("cell-state.json", "run-metadata.json")
}
