package io.embrace.android.embracesdk.minified

import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import java.util.concurrent.CopyOnWriteArrayList

object RecordingSpanExporter : SpanExporter {

    val exportedSpanNames: MutableList<String> = CopyOnWriteArrayList()

    override suspend fun export(telemetry: List<SpanData>): OperationResultCode {
        telemetry.forEach { exportedSpanNames += it.name }
        return OperationResultCode.Success
    }

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
