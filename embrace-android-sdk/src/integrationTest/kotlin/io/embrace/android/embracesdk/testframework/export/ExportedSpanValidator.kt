package io.embrace.android.embracesdk.testframework.export

import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.aliases.OtelJavaSpanData
import org.junit.Assert.assertEquals

internal class ExportedSpanValidator {

    fun validate(spanDataList: List<OtelJavaSpanData>, goldenFile: String) {
        val expected: List<Map<String, Any>> = readGoldenFile(goldenFile)
        val actual = spanDataList.map { it.representAsMap() }
        assertEquals(expected, actual)
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
            "totalAttributeCount" to attrs.size.toString(),
            "attributes" to attrs,
            "totalRecordedEvents" to totalRecordedEvents.toString(),
            "events" to events,
            "instrumentationScopeName" to instrumentationScopeInfo.name,
        )
    }

    private fun OtelJavaSpanData.representAttributes(): Map<String, String> {
        val ignoreList = listOf(
            EmbSessionAttributes.EMB_PROCESS_IDENTIFIER,
            EmbSessionAttributes.EMB_SESSION_PART_ID,
            EmbSessionAttributes.EMB_USER_SESSION_ID,
        )
        val attrs: Map<String, String> = attributes.asMap().map {
            it.key.key to it.value.toString()
        }.toMap()
            .filter { it.key !in ignoreList }
            .toSortedMap(compareBy { it })
        return attrs
    }
}
