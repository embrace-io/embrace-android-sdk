package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanExporter
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.tracing.export.SpanExporter
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpan

@OptIn(ExperimentalApi::class)
class KotlinSpanExportWrapper(
    private val impl: OtelJavaSpanExporter,
) : SpanExporter {

    override fun export(telemetry: List<ReadableSpan>): OperationResultCode {
        return impl.export(telemetry.map(ReadableSpan::toSpanData).toMutableList()).toOperationResultCode()
    }

    override fun forceFlush(): OperationResultCode = impl.flush().toOperationResultCode()
    override fun shutdown(): OperationResultCode = impl.shutdown().toOperationResultCode()
}
