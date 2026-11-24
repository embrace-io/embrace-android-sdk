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
        val result = logSink.storeLogs(telemetry.map(LogRecordData::toEmbracePayload))

        if (result == StoreDataResult.SUCCESS) {
            dispatchToExternalExporters(telemetry)
        }

        return when (result) {
            StoreDataResult.SUCCESS -> OperationResultCode.Success
            StoreDataResult.FAILURE -> OperationResultCode.Failure
        }
    }

    /**
     * Hands [telemetry] to the external exporters only: the [LogSink] never sees it.
     */
    fun exportExternally(telemetry: List<LogRecordData>) {
        if (exportCheck()) {
            dispatchToExternalExporters(telemetry)
        }
    }

    private fun dispatchToExternalExporters(telemetry: List<LogRecordData>) {
        if (externalExporters.isNotEmpty()) {
            val snapshot = telemetry.map { (it as? ReadableLogRecord)?.toLogRecordData() ?: it }
            externalExportDispatcher.dispatch(externalExporters) { it.export(snapshot) }
        }
    }

    override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode = exportInline(telemetry)

    override suspend fun forceFlush(): OperationResultCode {
        externalExportDispatcher.awaitPendingExports()
        return OperationResultCode.Success
    }

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
