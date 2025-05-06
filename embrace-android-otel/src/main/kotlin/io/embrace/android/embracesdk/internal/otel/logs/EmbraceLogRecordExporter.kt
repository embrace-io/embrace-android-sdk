package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

/**
 * Exports the given [LogRecordData] to a [LogSink]
 */
internal class EmbraceLogRecordExporter(
    private val logSink: LogSink,
    private val externalLogRecordExporter: LogRecordExporter?,
    private val exportCheck: () -> Boolean,
) : LogRecordExporter {

    override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
        if (!exportCheck()) {
            return CompletableResultCode.ofSuccess()
        }
        val result = logSink.storeLogs(logs.toList())
        if (externalLogRecordExporter != null && result == CompletableResultCode.ofSuccess()) {
            return externalLogRecordExporter.export(
                logs.filterNot {
                    it.hasEmbraceAttribute(PrivateSpan)
                }
            )
        }

        return result
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
