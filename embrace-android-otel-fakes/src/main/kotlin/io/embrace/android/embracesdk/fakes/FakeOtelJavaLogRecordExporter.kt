package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.aliases.OtelJavaCompletableResultCode
import io.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import io.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData

class FakeOtelJavaLogRecordExporter : OtelJavaLogRecordExporter {
    val exportedLogs: MutableList<OtelJavaLogRecordData> = mutableListOf()

    override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
        exportedLogs += logs
        return OtelJavaCompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode? = OtelJavaCompletableResultCode.ofSuccess()

    override fun shutdown(): OtelJavaCompletableResultCode = OtelJavaCompletableResultCode.ofSuccess()
}
