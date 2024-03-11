package io.embrace.android.embracesdk.internal.logs

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import java.util.concurrent.ConcurrentLinkedQueue

internal class LogSinkImpl : LogSink {
    private val storedLogs: ConcurrentLinkedQueue<EmbraceLogRecordData> = ConcurrentLinkedQueue()
    private var onLogsStored: (() -> Unit)? = null
    private val flushLock = Any()

    override fun storeLogs(logs: List<LogRecordData>): CompletableResultCode {
        try {
            storedLogs.addAll(logs.map { EmbraceLogRecordData(logRecordData = it) })
            onLogsStored?.invoke()
        } catch (t: Throwable) {
            return CompletableResultCode.ofFailure()
        }

        return CompletableResultCode.ofSuccess()
    }

    override fun completedLogs(): List<EmbraceLogRecordData> {
        return storedLogs.toList()
    }

    override fun flushLogs(max: Int?): List<EmbraceLogRecordData> {
        synchronized(flushLock) {
            val maxIndex = max?.let {
                minOf(storedLogs.size, it)
            } ?: storedLogs.size
            val flushedLogs = storedLogs.toList().subList(0, maxIndex)
            storedLogs.removeAll(flushedLogs.toSet())
            return flushedLogs
        }
    }

    override fun callOnLogsStored(onLogsStored: () -> Unit) {
        this.onLogsStored = onLogsStored
    }
}
