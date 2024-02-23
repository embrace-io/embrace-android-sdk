package io.embrace.android.embracesdk.internal.logs

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData

internal class LogSinkImpl : LogSink {
    private val storedLogs: MutableList<EmbraceLogRecordData> = mutableListOf()
    private var onLogsStored: (() -> Unit)? = null

    override fun storeLogs(logs: List<LogRecordData>): CompletableResultCode {
        try {
            synchronized(storedLogs) {
                storedLogs += logs.map { EmbraceLogRecordData(logRecordData = it) }
                onLogsStored?.invoke()
            }

        } catch (t: Throwable) {
            return CompletableResultCode.ofFailure()
        }

        return CompletableResultCode.ofSuccess()
    }

    override fun completedLogs(): List<EmbraceLogRecordData> {
        synchronized(storedLogs) {
            return storedLogs.toList()
        }
    }

    override fun flushLogs(): List<EmbraceLogRecordData> {
        synchronized(storedLogs) {
            val flushedLogs = storedLogs.toList()
            storedLogs.clear()
            return flushedLogs
        }
    }

    override fun callOnLogsStored(onLogsStored: () -> Unit) {
        this.onLogsStored = onLogsStored
    }
}
