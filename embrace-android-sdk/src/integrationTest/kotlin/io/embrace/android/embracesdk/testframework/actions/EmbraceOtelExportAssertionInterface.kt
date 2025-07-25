package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.testframework.export.ExportedSpanValidator
import io.embrace.android.embracesdk.testframework.export.FilteredLogExporter
import io.embrace.android.embracesdk.testframework.export.FilteredSpanExporter
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanData

/**
 * Provides assertions that can be used in integration tests to validate the behavior of the SDK,
 * specifically in what its OTel export looks like.
 */
internal class EmbraceOtelExportAssertionInterface(
    private val spanExporter: FilteredSpanExporter,
    private val logExporter: FilteredLogExporter,
    private val validator: ExportedSpanValidator = ExportedSpanValidator(),
) {

    fun awaitLogs(expectedCount: Int, filter: (OtelJavaLogRecordData) -> Boolean) = logExporter.awaitLogs(expectedCount, filter)
    fun awaitSpans(expectedCount: Int, filter: (OtelJavaSpanData) -> Boolean) = spanExporter.awaitSpans(expectedCount, filter)
    fun awaitSpansWithType(expectedCount: Int, type: EmbType) = spanExporter.awaitSpansWithType(expectedCount, type)

    /**
     * Asserts that the provided spans match the golden file.
     */
    fun assertSpansMatchGoldenFile(spans: List<OtelJavaSpanData>, goldenFile: String) {
        validator.validate(spans, goldenFile)
    }
}
