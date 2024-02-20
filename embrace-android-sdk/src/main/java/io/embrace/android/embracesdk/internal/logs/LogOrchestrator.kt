package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class LogOrchestrator(
    private val logOrchestratorScheduledWorker: ScheduledWorker,
    private val clock: Clock,
    private val sink: LogSinkImpl,
    private val deliveryService: DeliveryService
) {
    private var lastLogTime: Long = 0
    private var firstLogInBatchTime: Long = 0
    private var scheduledCheckFuture: ScheduledFuture<*>? = null

    init {
        sink.callOnLogsStored(::onLogsAdded)
    }
    private fun onLogsAdded() {
        lastLogTime = clock.now()
        if (firstLogInBatchTime == 0L) {
            firstLogInBatchTime = lastLogTime
        }
        if (logsShouldBeSent()) {
            sendLogs()
        } else {
            scheduleCheck()
        }
    }

    private fun logsShouldBeSent(): Boolean {
        return (
            (sink.completedLogs().size >= MAX_LOGS_PER_BATCH) ||
                (clock.now() - lastLogTime > MAX_INACTIVITY_TIME) ||
                (clock.now() - firstLogInBatchTime > MAX_BATCH_TIME)
            )
    }

    private fun sendLogs() {
        scheduledCheckFuture?.cancel(false)
        scheduledCheckFuture = null
        firstLogInBatchTime = 0

        // Sink is synchronized, so even if sendLogs is called concurrently, the logs
        // will be sent only once.
        val storedLogs = sink.flushLogs()

        if (storedLogs.isNotEmpty()) {
            deliveryService.sendLogs(LogPayload(logs = storedLogs))
        }
    }

    private fun scheduleCheck() {
        scheduledCheckFuture?.cancel(false)
        scheduledCheckFuture = logOrchestratorScheduledWorker.schedule<Unit>(
            {
                if (logsShouldBeSent()) {
                    sendLogs()
                }
            },
            MAX_INACTIVITY_TIME,
            TimeUnit.MILLISECONDS
        )
    }

    companion object {
        private const val MAX_LOGS_PER_BATCH = 50
        private const val MAX_BATCH_TIME = 5000L // In milliseconds
        private const val MAX_INACTIVITY_TIME = 2000L // In milliseconds
    }
}
