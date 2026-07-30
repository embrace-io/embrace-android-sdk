package io.embrace.android.embracesdk.testframework.export

import io.embrace.android.embracesdk.ResourceReader
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@OptIn(ExperimentalSerializationApi::class)
private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/**
 * Reads a golden file that contains a JSON array of objects, each of which represents one piece of
 * exported telemetry.
 */
internal fun readGoldenFile(goldenFile: String): List<Map<String, Any>> {
    val text = ResourceReader.readResourceAsText(goldenFile)
    @Suppress("UNCHECKED_CAST")
    return Json.parseToJsonElement(text).toAny() as List<Map<String, Any>>
}

/**
 * Recursively unwrap a [JsonElement] tree into plain Kotlin types (`String`, `Long`, `Double`,
 * `Boolean`, `Map<String, Any>`, `List<Any>`). JSON nulls are rendered as the string `"null"`
 * to preserve `Map<String, Any>` non-nullability.
 */
internal fun JsonElement.toAny(): Any = when (this) {
    JsonNull -> "null"
    is JsonObject -> mapValues { (_, v) -> v.toAny() }
    is JsonArray -> map { it.toAny() }
    is JsonPrimitive -> when {
        isString -> content
        else -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
    }
}

/**
 * Renders a golden file representation as pretty-printed JSON, so that a failing assertion can print
 * something that is directly pasteable into a golden file.
 */
internal fun List<Map<String, Any>>.toGoldenFileJson(): String =
    prettyJson.encodeToString(JsonElement.serializer(), toJsonElement())

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
    is List<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}
