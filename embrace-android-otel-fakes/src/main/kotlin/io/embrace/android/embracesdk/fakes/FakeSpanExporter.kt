package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter

@OptIn(ExperimentalApi::class)
class FakeSpanExporter : SpanExporter {

    val exportedSpans: MutableList<SpanData> = mutableListOf()

    override suspend fun export(telemetry: List<SpanData>): OperationResultCode {
        exportedSpans += telemetry
        return OperationResultCode.Success
    }

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
