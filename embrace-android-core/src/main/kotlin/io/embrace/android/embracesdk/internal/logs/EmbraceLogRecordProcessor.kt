package io.embrace.android.embracesdk.internal.logs

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.logs.export.LogRecordExporter

/**
 * [LogRecordProcessor] that exports log records it to the given [LogRecordExporter]
 */
internal class EmbraceLogRecordProcessor(
    private val logRecordExporter: LogRecordExporter
) : LogRecordProcessor {

    override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {
        logRecordExporter.export(mutableListOf(logRecord.toLogRecordData()))
    }
}
