package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.model.ReadableLogRecord

@OptIn(ExperimentalApi::class)
class FakeLogRecordExporter : LogRecordExporter {

    val exportedLogs: MutableList<ReadableLogRecord> = mutableListOf()

    override suspend fun export(telemetry: List<ReadableLogRecord>): OperationResultCode {
        exportedLogs += telemetry
        return OperationResultCode.Success
    }

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
}
