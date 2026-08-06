package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.export.InlineExporter
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbracePayload
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter

/**
 * Exports the given completed span to the given [SpanRepository] as well as any configured external exporter
 */
internal class DefaultSpanExporter(
    private val spanRepository: SpanRepository,
    private val externalExporters: List<SpanExporter>,
    private val exportCheck: () -> Boolean,
    private val externalExportDispatcher: ExternalExportDispatcher,
) : SpanExporter, InlineExporter<SpanData> {

    override fun exportInline(telemetry: List<SpanData>): OperationResultCode {
        if (!exportCheck()) {
            return OperationResultCode.Success
        }
        val result = spanRepository.storeCompletedOtelSpans(telemetry.map(SpanData::toEmbracePayload))

        if (result == StoreDataResult.SUCCESS && externalExporters.isNotEmpty()) {
            val exportable = telemetry.filterNot { it.attributes.containsKey(PrivateSpan.key) }
            externalExportDispatcher.dispatch(externalExporters) { it.export(exportable) }
        }

        return when (result) {
            StoreDataResult.SUCCESS -> OperationResultCode.Success
            StoreDataResult.FAILURE -> OperationResultCode.Failure
        }
    }

    override suspend fun export(telemetry: List<SpanData>): OperationResultCode = exportInline(telemetry)

    override suspend fun forceFlush(): OperationResultCode {
        externalExportDispatcher.awaitPendingExports()
        return OperationResultCode.Success
    }

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
