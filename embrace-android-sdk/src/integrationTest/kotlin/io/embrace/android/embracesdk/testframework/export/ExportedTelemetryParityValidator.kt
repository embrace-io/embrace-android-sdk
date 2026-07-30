package io.embrace.android.embracesdk.testframework.export

import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testframework.assertions.IGNORE_VALUE
import io.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.opentelemetry.kotlin.aliases.OtelJavaEventData
import io.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import io.opentelemetry.kotlin.aliases.OtelJavaSpanData
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
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
        val spanNames = spans.associate { it.spanId to it.name }
        validate(spans.map { it.representAsMap(spanNames) }, goldenFile)
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

    /**
     * Represents a span as a map. Trace & span IDs are random on every run, so any reference to
     * another span is represented by that span's name, resolved via [spanNames].
     */
    private fun OtelJavaSpanData.representAsMap(spanNames: Map<String, String>): Map<String, Any> {
        val attrs = attributes.representAsMap()
        return mapOf(
            "name" to name,
            "kind" to kind.toString(),
            "status" to status.statusCode.toString(),
            "startEpochNanos" to startEpochNanos.toString(),
            "endEpochNanos" to endEpochNanos.toString(),
            "hasEnded" to hasEnded().toString(),
            "parentSpanName" to when {
                !parentSpanContext.isValid -> NO_SPAN
                else -> spanNames[parentSpanId] ?: UNRESOLVED_SPAN
            },
            "totalAttributeCount" to attrs.size.toString(),
            "attributes" to attrs,
            "totalRecordedEvents" to totalRecordedEvents.toString(),
            "events" to events.map { it.representAsMap() },
            "totalRecordedLinks" to totalRecordedLinks.toString(),
            "links" to links.map { link ->
                mapOf(
                    "spanName" to (spanNames[link.spanContext.spanId] ?: UNRESOLVED_SPAN),
                    "attributes" to link.attributes.representAsMap(),
                )
            },
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
         * Represents a span reference that points at no span at all, e.g. the parent of a root span.
         */
        private const val NO_SPAN = ""

        /**
         * Represents a span reference that points at a span which wasn't part of the validated set,
         * and whose name therefore couldn't be resolved.
         */
        private const val UNRESOLVED_SPAN = "<not exported>"

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
            ExceptionAttributes.EXCEPTION_STACKTRACE,
        )
    }
}
