package io.embrace.analysis.maxims

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Shared parsing of numeric-ish launch attributes read off the cohort tap's JSON. */
internal object LaunchAttrs {

    /** Parses a launch attribute as a float, tolerantly: null when absent, `null`, or not a number. */
    internal fun attrFloat(launch: JsonObject, key: String): Double? {
        val raw = launch[key] as? JsonPrimitive ?: return null
        if (raw is JsonNull) {
            return null
        }
        if (!raw.isString && raw.content == "true") {
            return 1.0
        }
        if (!raw.isString && raw.content == "false") {
            return 0.0
        }
        return raw.content.trim().toDoubleOrNull()
    }
}
