package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.export.InlineExporter
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbracePayload
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter

/**
 * Exports the given completed span to the given [SpanRepository] as well as any configured external exporter.
 *
 * [decorate], if there is one, is applied to what the external exporters are handed, and never to what is stored.
 */
internal class DefaultSpanExporter(
    private val spanRepository: SpanRepository,
    private val externalExporters: List<SpanExporter>,
    private val exportCheck: () -> Boolean,
    private val externalExportDispatcher: ExternalExportDispatcher,
    private val decorate: ((SpanData) -> SpanData)? = null,
) : SpanExporter, InlineExporter<SpanData> {

    override fun exportInline(telemetry: List<SpanData>): OperationResultCode {
        if (!exportCheck()) {
            return OperationResultCode.Success
        }
        val result = spanRepository.storeCompletedOtelSpans(telemetry.map(SpanData::toEmbracePayload))

        if (result == StoreDataResult.SUCCESS && externalExporters.isNotEmpty()) {
            val exported = if (decorate != null) telemetry.map(decorate) else telemetry
            externalExportDispatcher.dispatch(externalExporters) { it.export(exported) }
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
