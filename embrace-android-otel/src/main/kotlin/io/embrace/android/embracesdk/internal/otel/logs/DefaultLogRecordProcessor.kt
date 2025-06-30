package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.embrace.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.embrace.opentelemetry.kotlin.logging.model.ReadWriteLogRecord

/**
 * A default implementation of a [LogRecordProcessor] that simply exports each log record to an exporter.
 */
@OptIn(ExperimentalApi::class)
internal class DefaultLogRecordProcessor(
    private val logRecordExporter: LogRecordExporter,
) : LogRecordProcessor {

    override fun forceFlush(): OperationResultCode = OperationResultCode.Success

    override fun onEmit(log: ReadWriteLogRecord, context: Context) {
        logRecordExporter.export(mutableListOf(log))
    }

    override fun shutdown(): OperationResultCode = OperationResultCode.Success
}
