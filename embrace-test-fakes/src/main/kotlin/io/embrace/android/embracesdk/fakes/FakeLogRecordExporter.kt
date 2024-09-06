package io.embrace.android.embracesdk.fakes

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

class FakeLogRecordExporter : LogRecordExporter {

    var exportedLogs: Collection<LogRecordData>? = null

    override fun export(logs: MutableCollection<LogRecordData>): CompletableResultCode {
        exportedLogs = logs
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
