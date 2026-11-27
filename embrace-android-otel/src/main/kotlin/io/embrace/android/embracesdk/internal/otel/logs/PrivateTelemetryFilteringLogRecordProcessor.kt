package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord

/**
 * Wraps a [LogRecordProcessor] supplied by the SDK user so that it never sees log records that are private
 * to Embrace, and sees nothing at all once [exportCheck] fails.
 */
internal class PrivateTelemetryFilteringLogRecordProcessor(
    private val delegate: LogRecordProcessor,
    private val exportCheck: () -> Boolean,
) : LogRecordProcessor by delegate {

    override fun onEmit(log: ReadWriteLogRecord, context: Context) {
        // a replayed state change is for a customer's exporters only, which get it rendered for them
        val attributes = log.attributes
        if (exportCheck() && !attributes.containsKey(PrivateSpan.key) && !attributes.containsKey(EXPORT_ONLY_KEY)) {
            delegate.onEmit(log, context)
        }
    }
}
