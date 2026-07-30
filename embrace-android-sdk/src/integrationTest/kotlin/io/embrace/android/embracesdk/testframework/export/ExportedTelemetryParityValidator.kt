package io.embrace.android.embracesdk.testframework.export

import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testframework.assertions.IGNORE_VALUE
import io.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.opentelemetry.kotlin.aliases.OtelJavaEventData
import io.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import io.opentelemetry.kotlin.aliases.OtelJavaSpanData
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals

/**
 * Validates exported telemetry against a golden file, for the purpose of proving that the
 * opentelemetry-kotlin 'compat' and 'KMP' implementations export identical telemetry.
 *
 * Both implementations assert against the same golden file, so a 1:1 match between them is proven
 * transitively. Only the properties in [DIVERGENT_ATTRIBUTES] and [REDACTED_ATTRIBUTES] are exempt.
 */
internal class ExportedTelemetryParityValidator {

    fun validateSpans(spans: List<OtelJavaSpanData>, goldenFile: String) {
        validate(spans.map { it.representAsMap() }, goldenFile)
    }

    fun validateLogs(logs: List<OtelJavaLogRecordData>, goldenFile: String) {
        validate(logs.map { it.representAsMap() }, goldenFile)
    }

    private fun validate(actual: List<Map<String, Any>>, goldenFile: String) {
        assertEquals(
            "Exported telemetry did not match golden file '$goldenFile'. Observed telemetry was:\n" +
                actual.toGoldenFileJson(),
            readGoldenFile(goldenFile),
            actual,
        )
    }

    private fun OtelJavaSpanData.representAsMap(): Map<String, Any> {
        val attrs = attributes.representAsMap()
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
            "events" to events.map { it.representAsMap() },
            "instrumentationScopeName" to instrumentationScopeInfo.name,
            "resourceAttributes" to resource.attributes.representAsMap(),
        )
    }

    private fun OtelJavaLogRecordData.representAsMap(): Map<String, Any> {
        val attrs = attributes.representAsMap()
        return mapOf(
            "body" to bodyValue?.asString().toString(),
            "severityNumber" to severity.severityNumber.toString(),
            "severityText" to severityText.toString(),
            "timestampEpochNanos" to timestampEpochNanos.toString(),
            "observedTimestampEpochNanos" to observedTimestampEpochNanos.toString(),
            "totalAttributeCount" to attrs.size.toString(),
            "attributes" to attrs,
            "instrumentationScopeName" to instrumentationScopeInfo.name,
            "resourceAttributes" to resource.attributes.representAsMap(),
        )
    }

    private fun OtelJavaEventData.representAsMap(): Map<String, Any> = mapOf(
        "name" to name,
        "epochNanos" to epochNanos.toString(),
        "attributes" to attributes.representAsMap(),
    )

    /**
     * Represents attributes as a sorted map of stringified values, dropping the attributes that only
     * one implementation emits and redacting the values that are non-deterministic between runs.
     */
    private fun OtelJavaAttributes.representAsMap(): Map<String, String> =
        asMap().entries
            .map { it.key.key to it.value.toString() }
            .filterNot { (key, _) -> key in DIVERGENT_ATTRIBUTES }
            .associate { (key, value) ->
                key to when (key) {
                    in REDACTED_ATTRIBUTES -> IGNORE_VALUE
                    else -> value
                }
            }
            .toSortedMap(compareBy { it })

    private companion object {

        /**
         * Attributes that are legitimately allowed to differ between the two implementations, and
         * are therefore dropped altogether.
         */
        private val DIVERGENT_ATTRIBUTES = setOf(
            "telemetry.sdk.name",
            "telemetry.sdk.language",
            "telemetry.sdk.version",
        )

        /**
         * Attributes whose values are non-deterministic between test runs, regardless of which
         * implementation is in use. The value is replaced with a placeholder rather than dropped so
         * that the presence of the attribute is still asserted.
         */
        private val REDACTED_ATTRIBUTES = setOf(
            SessionAttributes.SESSION_ID,
            EmbSessionAttributes.EMB_USER_SESSION_ID,
            EmbSessionAttributes.EMB_SESSION_PART_ID,
            EmbSessionAttributes.EMB_PROCESS_IDENTIFIER,
            EmbSessionAttributes.EMB_PRIVATE_SEQUENCE_ID,
            "log.record.uid",
        )
    }
}
