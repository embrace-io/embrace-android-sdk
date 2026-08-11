package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.export.InlineExporter
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.model.ReadableLogRecord

/**
 * Exports the given log record to a [LogSink]
 */
internal class DefaultLogRecordExporter(
    private val logSink: LogSink,
    private val externalExporters: List<LogRecordExporter>,
    private val exportCheck: () -> Boolean,
    private val externalExportDispatcher: ExternalExportDispatcher,
) : LogRecordExporter, InlineExporter<ReadableLogRecord> {

    override fun exportInline(telemetry: List<ReadableLogRecord>): OperationResultCode {
        if (!exportCheck()) {
            return OperationResultCode.Success
        }
        val result = logSink.storeLogs(telemetry.map(ReadableLogRecord::toEmbracePayload))

        if (result == StoreDataResult.SUCCESS && externalExporters.isNotEmpty()) {
            val exportable = telemetry.filterNot { it.attributes.containsKey(PrivateSpan.key) }
            externalExportDispatcher.dispatch(externalExporters) { it.export(exportable) }
        }

        return when (result) {
            StoreDataResult.SUCCESS -> OperationResultCode.Success
            StoreDataResult.FAILURE -> OperationResultCode.Failure
        }
    }

    override suspend fun export(telemetry: List<ReadableLogRecord>): OperationResultCode = exportInline(telemetry)

    override suspend fun forceFlush(): OperationResultCode {
        externalExportDispatcher.awaitPendingExports()
        return OperationResultCode.Success
    }

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
