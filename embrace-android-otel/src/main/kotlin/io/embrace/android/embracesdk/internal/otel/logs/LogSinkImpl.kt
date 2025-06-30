package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.otel.attrs.embSendMode
import io.embrace.android.embracesdk.internal.otel.schema.SendMode
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.utils.threadSafeTake
import java.util.concurrent.ConcurrentLinkedQueue

class LogSinkImpl : LogSink {
    private val storedLogs: ConcurrentLinkedQueue<Log> = ConcurrentLinkedQueue()
    private val logRequests: ConcurrentLinkedQueue<LogRequest<Log>> = ConcurrentLinkedQueue()
    private var onLogsStored: (() -> Unit)? = null
    private val flushLock = Any()

    override fun storeLogs(logs: List<Log>): StoreDataResult {
        try {
            logs.forEach { log ->
                val mode = log.attributes?.findAttributeValue(embSendMode.name)
                val sendMode = SendMode.fromString(mode)
                if (sendMode != SendMode.DEFAULT) {
                    logRequests.add(
                        LogRequest(
                            payload = log,
                            defer = sendMode == SendMode.DEFER
                        )
                    )
                } else {
                    storedLogs.add(log)
                }
            }
            onLogsStored?.invoke()
        } catch (t: Throwable) {
            return StoreDataResult.FAILURE
        }
        return StoreDataResult.SUCCESS
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

internal const val MAX_LOGS_PER_BATCH = 50
