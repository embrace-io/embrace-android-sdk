package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.tracing.data.SpanData
import io.embrace.opentelemetry.kotlin.tracing.export.SpanExporter

@OptIn(ExperimentalApi::class)
class FakeSpanExporter : SpanExporter {

    val exportedSpans: MutableList<SpanData> = mutableListOf()

    override fun export(telemetry: List<SpanData>): OperationResultCode {
        exportedSpans += telemetry
        return OperationResultCode.Success
    }

    override fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override fun shutdown(): OperationResultCode = OperationResultCode.Success
}
