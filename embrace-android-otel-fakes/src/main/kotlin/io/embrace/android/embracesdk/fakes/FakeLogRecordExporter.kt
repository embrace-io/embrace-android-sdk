package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.export.InlineExporter
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.model.ReadableLogRecord

class FakeLogRecordExporter : LogRecordExporter, InlineExporter<ReadableLogRecord> {

    val exportedLogs: MutableList<ReadableLogRecord> = mutableListOf()
    var forceFlushCount: Int = 0
        private set

    override fun exportInline(telemetry: List<ReadableLogRecord>): OperationResultCode {
        exportedLogs += telemetry
        return OperationResultCode.Success
    }

    override suspend fun export(telemetry: List<ReadableLogRecord>): OperationResultCode = exportInline(telemetry)

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success

    override suspend fun forceFlush(): OperationResultCode {
        forceFlushCount++
        return OperationResultCode.Success
    }
}
