package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.arch.schema.SendMode
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.utils.threadSafeToList
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import java.util.concurrent.ConcurrentLinkedQueue

class LogSinkImpl : LogSink {
    private val storedLogs: ConcurrentLinkedQueue<Log> = ConcurrentLinkedQueue()
    private val logRequests: ConcurrentLinkedQueue<LogRequest<Log>> = ConcurrentLinkedQueue()
    private var onLogsStored: (() -> Unit)? = null
    private val flushLock = Any()

    override fun storeLogs(logs: List<Log>): StoreDataResult {
        try {
            logs.forEach { log ->
                val mode = log.attributes?.findAttributeValue(EmbSessionAttributes.EMB_PRIVATE_SEND_MODE)
                val sendMode = SendMode.fromString(mode)
                if (sendMode != SendMode.DEFAULT) {
                    logRequests.add(
                        LogRequest(
                            payload = log,
                            defer = sendMode == SendMode.DEFER,
                        ),
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
        return storedLogs.threadSafeToList()
    }

    override fun storedLogCount(): Int = storedLogs.size

    override fun flushBatch(): List<Log> {
        synchronized(flushLock) {
            val flushedLogs = ArrayList<Log>(MAX_LOGS_PER_BATCH)
            while (flushedLogs.size < MAX_LOGS_PER_BATCH) {
                val log = storedLogs.poll() ?: break
                flushedLogs.add(log)
            }
            return flushedLogs
        }
    }

    override fun pollUnbatchedLog(): LogRequest<Log>? = logRequests.poll()

    override fun registerLogStoredCallback(onLogsStored: () -> Unit) {
        this.onLogsStored = onLogsStored
    }
}

internal const val MAX_LOGS_PER_BATCH = 50
