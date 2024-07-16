package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

/**
 * Exports the given [LogRecordData] to a [LogSink]
 */
internal class EmbraceLogRecordExporter(
    private val logSink: LogSink,
    private val externalLogRecordExporter: LogRecordExporter
) : LogRecordExporter {
    override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
        val result = logSink.storeLogs(logs.toList())
        if (result == CompletableResultCode.ofSuccess()) {
            return externalLogRecordExporter.export(
                logs.filterNot {
                    it.hasFixedAttribute(PrivateSpan)
                }
            )
        }

        return result
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
