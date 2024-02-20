package io.embrace.android.embracesdk.internal.logs

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

/**
 * Exports the given [LogRecordData] to a [LogSink]
 */
internal class EmbraceLogRecordExporter(private val logSink: LogSink) : LogRecordExporter {
    override fun export(logs: Collection<LogRecordData>): CompletableResultCode =
        logSink.storeLogs(logs.toList())

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
