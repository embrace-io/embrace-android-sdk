package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.testframework.export.ExportedSpanValidator
import io.embrace.android.embracesdk.testframework.export.FilteredSpanExporter
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * Provides assertions that can be used in integration tests to validate the behavior of the SDK,
 * specifically in what its OTel export looks like.
 */
internal class EmbraceOtelExportAssertionInterface(
    private val spanExporter: FilteredSpanExporter,
    private val validator: ExportedSpanValidator = ExportedSpanValidator()
) {

    /**
     * Retrieves spans with the specified type and waits until either the expected
     * number of spans is reached or a timeout is exceeded.
     */
    fun awaitSpansWithType(type: EmbType, expectedCount: Int): List<SpanData> {
        return spanExporter.awaitSpansWithType(type, expectedCount)
    }

    /**
     * Asserts that the provided spans match the golden file.
     */
    fun assertSpansMatchGoldenFile(spans: List<SpanData>, goldenFile: String) {
        validator.validate(spans, goldenFile)
    }
}
