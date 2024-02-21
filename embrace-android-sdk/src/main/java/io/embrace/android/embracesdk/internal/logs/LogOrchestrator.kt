package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.lang.Long.min
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class LogOrchestrator(
    private val logOrchestratorScheduledWorker: ScheduledWorker,
    private val clock: Clock,
    private val sink: LogSink,
    private val deliveryService: DeliveryService
) {
    @Volatile
    private var lastLogTime: Long = 0

    @Volatile
    private var firstLogInBatchTime: Long = 0

    @Volatile
    private var scheduledCheckFuture: ScheduledFuture<*>? = null

    init {
        sink.callOnLogsStored(::onLogsAdded)
    }

    private fun onLogsAdded() {
        lastLogTime = clock.now()
        if (firstLogInBatchTime == 0L) {
            firstLogInBatchTime = lastLogTime
        }
        if (!sendLogsIfNeeded()) {
            // If [firstLogInBatchTime] was cleared by a concurrent call to [sendLogsIfNeeded]
            // then update it to the time of this log
            if (firstLogInBatchTime == 0L) {
                firstLogInBatchTime = lastLogTime
            }
            scheduleCheck()
        }
    }

    /**
     * Returns true if logs were sent, false otherwise
     */
    @Synchronized
    private fun sendLogsIfNeeded(): Boolean {
        val now = clock.now()
        val shouldSendLogs = sink.completedLogs().size >= MAX_LOGS_PER_BATCH ||
            now - lastLogTime > MAX_INACTIVITY_TIME ||
            now - firstLogInBatchTime > MAX_BATCH_TIME

        if (!shouldSendLogs) {
            // None of the conditions to send the logs is met
            return false
        }

        scheduledCheckFuture?.cancel(false)
        scheduledCheckFuture = null
        firstLogInBatchTime = 0

        val storedLogs = sink.flushLogs()

        if (storedLogs.isNotEmpty()) {
            deliveryService.sendLogs(LogPayload(logs = storedLogs))
        }

        return true
    }

    private fun scheduleCheck() {
        val now = clock.now()
        val nextBatchCheck = MAX_BATCH_TIME - (now - firstLogInBatchTime)
        val nextInactivityCheck = MAX_INACTIVITY_TIME - (now - lastLogTime)
        scheduledCheckFuture?.cancel(false)
        scheduledCheckFuture = logOrchestratorScheduledWorker.schedule<Unit>(
            ::sendLogsIfNeeded,
            min(nextBatchCheck, nextInactivityCheck),
            TimeUnit.MILLISECONDS
        )
    }

    companion object {
        private const val MAX_LOGS_PER_BATCH = 50
        private const val MAX_BATCH_TIME = 5000L // In milliseconds
        private const val MAX_INACTIVITY_TIME = 2000L // In milliseconds
    }
}
