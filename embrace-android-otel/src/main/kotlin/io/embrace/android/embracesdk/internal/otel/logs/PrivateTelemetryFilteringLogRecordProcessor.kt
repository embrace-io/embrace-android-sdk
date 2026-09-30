package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord

/**
 * Wraps a [LogRecordProcessor] supplied by the SDK user so that it never sees log records that are private
 * to Embrace.
 */
internal class PrivateTelemetryFilteringLogRecordProcessor(
    private val delegate: LogRecordProcessor,
) : LogRecordProcessor by delegate {

    override fun onEmit(log: ReadWriteLogRecord, context: Context) {
        if (!log.attributes.containsKey(PrivateSpan.key)) {
            delegate.onEmit(log, context)
        }
    }
}
