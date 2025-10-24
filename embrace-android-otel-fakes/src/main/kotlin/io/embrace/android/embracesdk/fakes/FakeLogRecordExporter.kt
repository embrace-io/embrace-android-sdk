package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.embrace.opentelemetry.kotlin.logging.model.ReadableLogRecord

@OptIn(ExperimentalApi::class)
class FakeLogRecordExporter : LogRecordExporter {

    val exportedLogs: MutableList<ReadableLogRecord> = mutableListOf()

    override fun export(telemetry: List<ReadableLogRecord>): OperationResultCode {
        exportedLogs += telemetry
        return OperationResultCode.Success
    }

    override fun shutdown(): OperationResultCode = OperationResultCode.Success

    override fun forceFlush(): OperationResultCode = OperationResultCode.Success
}
