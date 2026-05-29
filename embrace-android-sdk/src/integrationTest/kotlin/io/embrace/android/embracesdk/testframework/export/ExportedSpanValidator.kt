package io.embrace.android.embracesdk.testframework.export

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.aliases.OtelJavaSpanData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals

internal class ExportedSpanValidator {

    fun validate(spanDataList: List<OtelJavaSpanData>, goldenFile: String) {
        val expected: List<Map<String, Any>> = readExpectedSpan(goldenFile)
        val actual = spanDataList.map { it.representAsMap() }
        assertEquals(expected, actual)
    }

    private fun readExpectedSpan(goldenFile: String): List<Map<String, Any>> {
        val text = ResourceReader.readResource(goldenFile).bufferedReader().use { it.readText() }
        @Suppress("UNCHECKED_CAST")
        return Json.parseToJsonElement(text).toAny() as List<Map<String, Any>>
    }

    /**
     * Recursively unwrap a [JsonElement] tree into plain Kotlin types (`String`, `Long`, `Double`,
     * `Boolean`, `Map<String, Any>`, `List<Any>`). JSON nulls are rendered as the string `"null"`
     * to preserve `Map<String, Any>` non-nullability.
     */
    private fun JsonElement.toAny(): Any = when (this) {
        JsonNull -> "null"
        is JsonObject -> mapValues { (_, v) -> v.toAny() }
        is JsonArray -> map { it.toAny() }
        is JsonPrimitive -> when {
            isString -> content
            else -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
        }
    }

    private fun OtelJavaSpanData.representAsMap(): Map<String, Any> {
        val attrs: Map<String, String> = representAttributes()
        return mapOf(
            "name" to name,
            "kind" to kind.toString(),
            "status" to status.statusCode.toString(),
            "startEpochNanos" to startEpochNanos.toString(),
            "endEpochNanos" to endEpochNanos.toString(),
            "hasEnded" to hasEnded().toString(),
            "totalAttributeCount" to totalAttributeCount.toString(),
            "attributes" to attrs,
            "totalRecordedEvents" to totalRecordedEvents.toString(),
            "events" to events,
            "instrumentationScopeName" to instrumentationScopeInfo.name,
        )
    }

    private fun OtelJavaSpanData.representAttributes(): Map<String, String> {
        val ignoreList = listOf(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, EmbSessionAttributes.EMB_PRIVATE_SEQUENCE_ID, "session.id")
        val attrs: Map<String, String> = attributes.asMap().map {
            it.key.key to it.value.toString()
        }.toMap()
            .filter { it.key !in ignoreList }
            .toSortedMap(compareBy { it })
        return attrs
    }
}
