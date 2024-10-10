package io.embrace.android.embracesdk.testframework.export

import com.squareup.moshi.Types
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.utils.threadLocal
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.Assert.assertEquals

internal class ExportedSpanValidator {

    private val serializer: TestPlatformSerializer by threadLocal {
        TestPlatformSerializer()
    }

    private val type =
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val listType = Types.newParameterizedType(List::class.java, type)

    fun validate(spanDataList: List<SpanData>, goldenFile: String) {
        val expected: List<Map<String, Any>> = readExpectedSpan(goldenFile)
        val actual = spanDataList.map { it.representAsMap() }
        assertEquals(expected, actual)
    }

    private fun readExpectedSpan(goldenFile: String): List<Map<String, String>> {
        val inputStream = ResourceReader.readResource(goldenFile)
        return serializer.fromJson(inputStream, listType)
    }

    private fun SpanData.representAsMap(): Map<String, Any> {
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

    private fun SpanData.representAttributes(): Map<String, String> {
        val ignoreList = listOf("emb.process_identifier", "emb.private.sequence_id")
        val attrs: Map<String, String> = attributes.asMap().map {
            it.key.key to it.value.toString()
        }.toMap()
            .filter { it.key !in ignoreList }
            .toSortedMap(compareBy { it })
        return attrs
    }
}
