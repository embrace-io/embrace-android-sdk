package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.export.InlineExporter
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.logging.export.LogRecordExporter

class FakeLogRecordExporter : LogRecordExporter, InlineExporter<LogRecordData> {

    val exportedLogs: MutableList<LogRecordData> = mutableListOf()
    var forceFlushCount: Int = 0
        private set

    override fun exportInline(telemetry: List<LogRecordData>): OperationResultCode {
        exportedLogs += telemetry
        return OperationResultCode.Success
    }

    override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode = exportInline(telemetry)

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success

    override suspend fun forceFlush(): OperationResultCode {
        forceFlushCount++
        return OperationResultCode.Success
    }
}
