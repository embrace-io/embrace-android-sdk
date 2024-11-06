package io.embrace.android.exampleapp

import android.util.Log
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

class LogcatLogRecordExporter : LogRecordExporter {

    override fun export(logRecords: Collection<LogRecordData>): CompletableResultCode {
        for (logRecord in logRecords) {
            Log.i(
                "LogcatLogRecordExporter",
                "LogRecord: ${logRecord.bodyValue?.asString()}, Severity: ${logRecord.severity}, Attributes: ${logRecord.attributes}"
            )
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }
}
