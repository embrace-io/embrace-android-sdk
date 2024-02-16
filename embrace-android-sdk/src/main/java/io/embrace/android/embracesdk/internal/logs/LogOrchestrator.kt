package io.embrace.android.embracesdk.internal.logs

import androidx.annotation.VisibleForTesting
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class LogOrchestrator(
    private val logOrchestratorScheduledWorker: ScheduledWorker,
    private val clock: Clock,
    private val sink: LogSinkImpl
) {
    private var lastLogTime: Long = 0
    private var inactivityFuture: ScheduledFuture<*>? = null
    private var batchTimeFuture: ScheduledFuture<*>? = null

    init {
        sink.callOnLogsStored(::onLogsAdded)
    }
    private fun onLogsAdded() {
        lastLogTime = clock.now()
        if (sink.completedLogs().size >= MAX_LOGS_PER_BATCH) {
            sendLogs()
        } else {
            scheduleInactivityCheck()
            if (batchTimeFuture == null) {
                scheduleBatchTimeCheck()
            }
        }
    }

    @VisibleForTesting
    fun sendLogs() {
        inactivityFuture?.cancel(false)
        batchTimeFuture?.cancel(false)

        val storedLogs = sink.flushLogs()

        if (storedLogs.isNotEmpty()) {
            // TBD: Send logs to DeliveryService
        }
    }

    private fun scheduleInactivityCheck() {
        inactivityFuture?.cancel(false)
        inactivityFuture = logOrchestratorScheduledWorker.schedule<Unit>(
            {
                if (clock.now() - lastLogTime > MAX_INACTIVITY_TIME) {
                    sendLogs()
                }
            },
            MAX_INACTIVITY_TIME,
            TimeUnit.MILLISECONDS
        )
    }

    private fun scheduleBatchTimeCheck() {
        batchTimeFuture?.cancel(false)
        batchTimeFuture = logOrchestratorScheduledWorker.schedule<Unit>(
            ::sendLogs,
            MAX_BATCH_TIME,
            TimeUnit.MILLISECONDS
        )
    }

    companion object {
        private const val MAX_LOGS_PER_BATCH = 50
        private const val MAX_BATCH_TIME = 5000L // In milliseconds
        private const val MAX_INACTIVITY_TIME = 2000L // In milliseconds
    }
}
