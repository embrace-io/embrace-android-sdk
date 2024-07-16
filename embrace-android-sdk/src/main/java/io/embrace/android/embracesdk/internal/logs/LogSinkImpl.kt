package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.arch.schema.SendImmediately
import io.embrace.android.embracesdk.internal.logs.LogOrchestratorImpl.Companion.MAX_LOGS_PER_BATCH
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.utils.threadSafeTake
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import java.util.concurrent.ConcurrentLinkedQueue

internal class LogSinkImpl : LogSink {
    private val storedLogs: ConcurrentLinkedQueue<Log> = ConcurrentLinkedQueue()
    private val nonbatchedLogs: ConcurrentLinkedQueue<Log> = ConcurrentLinkedQueue()
    private var onLogsStored: (() -> Unit)? = null
    private val flushLock = Any()

    override fun storeLogs(logs: List<LogRecordData>): CompletableResultCode {
        try {
            logs.forEach { log ->
                if (log.hasFixedAttribute(SendImmediately)) {
                    nonbatchedLogs.add(log.toNewPayload())
                } else {
                    storedLogs.add(log.toNewPayload())
                }
            }
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
            val batchSize = minOf(storedLogs.size, MAX_LOGS_PER_BATCH)
            val flushedLogs = storedLogs.threadSafeTake(batchSize)
            storedLogs.removeAll(flushedLogs.toSet())
            return flushedLogs
        }
    }

    override fun pollNonbatchedLog(): Log? = nonbatchedLogs.poll()

    override fun registerLogStoredCallback(onLogsStored: () -> Unit) {
        this.onLogsStored = onLogsStored
    }
}
