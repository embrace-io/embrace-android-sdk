package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.export.InlineExporter
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter

class FakeSpanExporter : SpanExporter, InlineExporter<SpanData> {

    val exportedSpans: MutableList<SpanData> = mutableListOf()
    var forceFlushCount: Int = 0
        private set

    override fun exportInline(telemetry: List<SpanData>): OperationResultCode {
        exportedSpans += telemetry
        return OperationResultCode.Success
    }

    override suspend fun export(telemetry: List<SpanData>): OperationResultCode = exportInline(telemetry)

    override suspend fun forceFlush(): OperationResultCode {
        forceFlushCount++
        return OperationResultCode.Success
    }
    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
