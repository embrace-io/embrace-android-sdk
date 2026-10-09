package io.embrace.android.embracesdk.minified

import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import java.util.concurrent.CopyOnWriteArrayList

object RecordingSpanExporter : SpanExporter {

    val exportedSpans: MutableList<ExportedSpan> = CopyOnWriteArrayList()

    override suspend fun export(telemetry: List<SpanData>): OperationResultCode {
        telemetry.forEach {
            exportedSpans += ExportedSpan(
                name = it.name,
                sdkMode = it.resource.attributes["telemetry.sdk.mode"] as? String,
            )
        }
        return OperationResultCode.Success
    }

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
