package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbracePayload
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.tracing.data.SpanData
import io.embrace.opentelemetry.kotlin.tracing.export.SpanExporter

/**
 * Exports the given completed span to the given [SpanSink] as well as any configured external exporter
 */
@ExperimentalApi
internal class DefaultSpanExporter(
    private val spanSink: SpanSink,
    private val externalExporters: List<SpanExporter>,
    private val exportCheck: () -> Boolean,
) : SpanExporter {

    @Synchronized
    override fun export(telemetry: List<SpanData>): OperationResultCode {
        if (!exportCheck()) {
            return OperationResultCode.Success
        }
        var result = spanSink.storeCompletedSpans(telemetry.map(SpanData::toEmbracePayload))
        if (externalExporters.isNotEmpty() && result == StoreDataResult.SUCCESS) {
            EmbTrace.trace("otel-external-export") {
                externalExporters.forEach { exporter ->
                    try {
                        exporter.export(
                            telemetry.filterNot {
                                it.attributes.containsKey(PrivateSpan.key.name)
                            }
                        )
                    } catch (ignored: Throwable) {
                        result = StoreDataResult.FAILURE
                    }
                }
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
