package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter

class FakeSpanExporter : SpanExporter {

    val exportedSpans: MutableList<SpanData> = mutableListOf()

    override fun export(telemetry: List<SpanData>): OperationResultCode {
        exportedSpans += telemetry
        return OperationResultCode.Success
    }

    override fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override fun shutdown(): OperationResultCode = OperationResultCode.Success
}
