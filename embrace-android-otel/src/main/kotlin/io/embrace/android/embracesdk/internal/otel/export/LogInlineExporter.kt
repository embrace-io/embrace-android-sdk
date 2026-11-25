package io.embrace.android.embracesdk.internal.otel.export

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData

/**
 * An [InlineExporter] of log records that can add attributes to what it stores in the SDK's sink without adding them to
 * the record, which is what an app's own exporters are handed, and that lets the record change after it is stored and
 * before it is handed to them.
 */
interface LogInlineExporter : InlineExporter<LogRecordData> {

    /**
     * Stores [log] in the SDK's sink with [payloadAttributes] after its own, calls [afterStore], and then exports [log] to
     * anything of the app's with whatever [afterStore] put on it, which the sink's copy does not have. [afterStore] is
     * called once, unless export is disabled, in which case nothing is stored or exported and it is not called.
     */
    fun exportInline(
        log: LogRecordData,
        payloadAttributes: List<Attribute>,
        afterStore: () -> Unit,
    ): OperationResultCode
}
