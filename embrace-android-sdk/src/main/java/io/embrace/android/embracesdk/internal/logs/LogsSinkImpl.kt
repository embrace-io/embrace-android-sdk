package io.embrace.android.embracesdk.internal.logs

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData

internal class LogsSinkImpl : LogsSink {
    private val logs: MutableList<LogRecordData> = mutableListOf()

    override fun storeLogs(logs: List<LogRecordData>): CompletableResultCode {
        try {
            synchronized(logs) {
                this.logs.addAll(logs)
            }
        } catch (t: Throwable) {
            return CompletableResultCode.ofFailure()
        }

        return CompletableResultCode.ofSuccess()
    }

    override fun logs(): List<LogRecordData> {
        synchronized(logs) {
            return logs.toList()
        }
    }

    override fun flushLogs(): List<LogRecordData> {
        synchronized(logs) {
            val flushedLogs = logs.toList()
            logs.clear()
            return flushedLogs
        }
    }
}