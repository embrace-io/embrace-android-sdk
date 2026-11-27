package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.export.InlineExporter
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
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
) : LogRecordExporter, InlineExporter<LogRecordData> {

    override fun exportInline(telemetry: List<LogRecordData>): OperationResultCode {
        if (!exportCheck()) {
            return OperationResultCode.Success
        }
        // a record meant for a customer's exporters alone never reaches Embrace's own payload
        val (exportOnly, embraceLogs) = if (telemetry.none { it.attributes.containsKey(EXPORT_ONLY_KEY) }) {
            Pair(emptyList(), telemetry)
        } else {
            telemetry.partition { it.attributes.containsKey(EXPORT_ONLY_KEY) }
        }
        val result = if (embraceLogs.isEmpty()) {
            StoreDataResult.SUCCESS
        } else {
            logSink.storeLogs(embraceLogs.map(LogRecordData::toEmbracePayload))
        }

        if (result == StoreDataResult.SUCCESS && externalExporters.isNotEmpty()) {
            val snapshot = telemetry.map { (it as? ReadableLogRecord)?.toLogRecordData() ?: it }
            externalExportDispatcher.dispatch(externalExporters) { it.export(snapshot) }
        } else if (exportOnly.isNotEmpty() && externalExporters.isNotEmpty()) {
            val snapshot = exportOnly.map { (it as? ReadableLogRecord)?.toLogRecordData() ?: it }
            externalExportDispatcher.dispatch(externalExporters) { it.export(snapshot) }
        }

        return when (result) {
            StoreDataResult.SUCCESS -> OperationResultCode.Success
            StoreDataResult.FAILURE -> OperationResultCode.Failure
        }
    }

    override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode = exportInline(telemetry)

    override suspend fun forceFlush(): OperationResultCode {
        externalExportDispatcher.awaitPendingExports()
        return OperationResultCode.Success
    }

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
