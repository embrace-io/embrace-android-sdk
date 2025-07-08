package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbracePayload
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.tracing.export.SpanExporter
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpan

/**
 * Exports the given completed span to the given [SpanSink] as well as any configured external exporter
 */
@ExperimentalApi
internal class DefaultSpanExporter(
    private val spanSink: SpanSink,
    private val externalSpanExporter: SpanExporter?,
    private val exportCheck: () -> Boolean,
) : SpanExporter {

    @Synchronized
    override fun export(telemetry: List<ReadableSpan>): OperationResultCode {
        if (!exportCheck()) {
            return OperationResultCode.Success
        }
        val result = spanSink.storeCompletedSpans(telemetry.map(ReadableSpan::toEmbracePayload))
        if (externalSpanExporter != null && result == StoreDataResult.SUCCESS) {
            return EmbTrace.trace("otel-external-export") {
                externalSpanExporter.export(
                    telemetry.filterNot {
                        it.attributes.containsKey(PrivateSpan.key.name)
                    }
                )
            }
        }
        return when (result) {
            StoreDataResult.SUCCESS -> OperationResultCode.Success
            StoreDataResult.FAILURE -> OperationResultCode.Failure
        }
    }

    override fun forceFlush(): OperationResultCode = OperationResultCode.Success

    @Synchronized
    override fun shutdown(): OperationResultCode = OperationResultCode.Success
}
