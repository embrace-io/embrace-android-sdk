package io.embrace.android.embracesdk.testframework.export

import com.squareup.moshi.Types
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.Assert.assertEquals

internal class ExportedSpanValidator(
    private val serializer: TestPlatformSerializer
) {
    private val type =
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)

    fun validate(spanData: SpanData, goldenFile: String) {
        val expected: Map<String, Any> = readExpectedSpan(goldenFile).toMap()
        val actual: Map<String, Any> = spanData.representAsMap().toMap()
        assertEquals(expected, actual)
    }

    private fun readExpectedSpan(goldenFile: String): Map<String, String> {
        val inputStream = ResourceReader.readResource(goldenFile)
        return serializer.fromJson(inputStream, type)
    }

    private fun SpanData.representAsMap(): Map<String, Any> = mapOf(
        "name" to name,
        "kind" to kind,
        "attributes" to attributes.asMap()
            .filter { it.key.key != "emb.process_identifier" } // filter out unwanted attrs
            .toSortedMap(compareBy { it.key }),
    ).mapValues { it.value.toString() }
}
