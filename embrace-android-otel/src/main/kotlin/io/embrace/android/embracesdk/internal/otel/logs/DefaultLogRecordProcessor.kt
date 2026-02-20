package io.embrace.android.embracesdk.internal.otel.logs

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import kotlinx.coroutines.runBlocking

/**
 * A default implementation of a [LogRecordProcessor] that simply exports each log record to an exporter.
 */
@OptIn(ExperimentalApi::class)
internal class DefaultLogRecordProcessor(
    private val logRecordExporter: LogRecordExporter,
) : LogRecordProcessor {

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

    override fun onEmit(log: ReadWriteLogRecord, context: Context) {
        runBlocking { logRecordExporter.export(mutableListOf(log)) }
    }

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
