package io.embrace.android.embracesdk.internal.otel.logs

import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord

/**
 * A default implementation of a [LogRecordProcessor] that simply exports each log record to an exporter.
 */
internal class DefaultLogRecordProcessor(
    private val logRecordExporter: LogRecordExporter,
) : LogRecordProcessor {

    override fun forceFlush(): OperationResultCode = OperationResultCode.Success

    override fun onEmit(log: ReadWriteLogRecord, context: Context) {
        logRecordExporter.export(mutableListOf(log))
    }

    override fun shutdown(): OperationResultCode = OperationResultCode.Success
}
