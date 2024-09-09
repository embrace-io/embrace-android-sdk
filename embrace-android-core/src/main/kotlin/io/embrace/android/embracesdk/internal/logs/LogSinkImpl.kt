package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.arch.schema.SendMode
import io.embrace.android.embracesdk.internal.arch.schema.toSendMode
import io.embrace.android.embracesdk.internal.opentelemetry.embSendMode
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.utils.threadSafeTake
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import java.util.concurrent.ConcurrentLinkedQueue

class LogSinkImpl : LogSink {
    private val storedLogs: ConcurrentLinkedQueue<Log> = ConcurrentLinkedQueue()
    private val logRequests: ConcurrentLinkedQueue<LogRequest<Log>> = ConcurrentLinkedQueue()
    private var onLogsStored: (() -> Unit)? = null
    private val flushLock = Any()

    override fun storeLogs(logs: List<LogRecordData>): CompletableResultCode {
        try {
            logs.forEach { log ->
                val sendMode = log.attributes[embSendMode.attributeKey]?.toSendMode() ?: SendMode.DEFAULT
                if (sendMode != SendMode.DEFAULT) {
                    logRequests.add(
                        LogRequest(
                            payload = log.toNewPayload(),
                            defer = sendMode == SendMode.DEFER
                        )
                    )
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

    override fun logsForNextBatch(): List<Log> {
        return storedLogs.toList()
    }

    override fun flushBatch(): List<Log> {
        synchronized(flushLock) {
            val batchSize = minOf(storedLogs.size, MAX_LOGS_PER_BATCH)
            val flushedLogs = storedLogs.threadSafeTake(batchSize)
            storedLogs.removeAll(flushedLogs.toSet())
            return flushedLogs
        }
    }

    override fun pollUnbatchedLog(): LogRequest<Log>? = logRequests.poll()

    override fun registerLogStoredCallback(onLogsStored: () -> Unit) {
        this.onLogsStored = onLogsStored
    }
}
