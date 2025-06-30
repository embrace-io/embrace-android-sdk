package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordProcessor
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaReadWriteLogRecord

/**
 * [LogRecordProcessor] that exports log records it to the given [LogRecordExporter]
 */
internal class EmbraceOtelJavaLogRecordProcessor(
    private val logRecordExporter: OtelJavaLogRecordExporter,
) : OtelJavaLogRecordProcessor {

    override fun onEmit(context: OtelJavaContext, logRecord: OtelJavaReadWriteLogRecord) {
        logRecordExporter.export(mutableListOf(logRecord.toLogRecordData()))
    }
}
