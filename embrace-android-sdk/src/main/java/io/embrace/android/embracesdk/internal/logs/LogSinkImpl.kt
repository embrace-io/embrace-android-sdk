package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.logs.LogOrchestrator.Companion.MAX_LOGS_PER_BATCH
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import java.util.concurrent.ConcurrentLinkedQueue

internal class LogSinkImpl : LogSink {
    private val storedLogs: ConcurrentLinkedQueue<Log> = ConcurrentLinkedQueue()
    private var onLogsStored: (() -> Unit)? = null
    private val flushLock = Any()

    override fun storeLogs(logs: List<LogRecordData>): CompletableResultCode {
        try {
            storedLogs.addAll(logs.map { it.toNewPayload() })
            onLogsStored?.invoke()
        } catch (t: Throwable) {
            return CompletableResultCode.ofFailure()
        }

        return CompletableResultCode.ofSuccess()
    }

    override fun completedLogs(): List<Log> {
        return storedLogs.toList()
    }

    override fun flushLogs(): List<Log> {
        synchronized(flushLock) {
            val currentSize = storedLogs.size
            val maxIndex = minOf(currentSize, MAX_LOGS_PER_BATCH)
            val flushedLogs = storedLogs.toList().take(maxIndex)
            storedLogs.removeAll(flushedLogs.toSet())
            return flushedLogs
        }
    }

    override fun callOnLogsStored(onLogsStored: () -> Unit) {
        this.onLogsStored = onLogsStored
    }
}
