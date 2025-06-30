package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.embrace.opentelemetry.kotlin.logging.model.ReadableLogRecord

@OptIn(ExperimentalApi::class)
class FakeLogRecordExporter : LogRecordExporter {

    val exportedLogs: MutableList<ReadableLogRecord> = mutableListOf()
    var flushCount = 0
    var shutdownCount = 0

    override fun export(telemetry: List<ReadableLogRecord>): OperationResultCode {
        exportedLogs += telemetry
        return OperationResultCode.Success
    }

    override fun shutdown(): OperationResultCode {
        shutdownCount += 1
        return OperationResultCode.Success
    }

    override fun forceFlush(): OperationResultCode {
        flushCount += 1
        return OperationResultCode.Success
    }
}
