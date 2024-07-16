package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.capture.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import java.lang.Long.min
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal interface LogOrchestrator {

    /**
     * Flushes immediately any log still in the sink
     */
    fun flush(saveOnly: Boolean)
}

internal class LogOrchestratorImpl(
    private val logOrchestratorScheduledWorker: ScheduledWorker,
    private val clock: Clock,
    private val sink: LogSink,
    private val deliveryService: DeliveryService,
    private val logEnvelopeSource: LogEnvelopeSource,
) : LogOrchestrator {
    @Volatile
    private var lastLogTime: AtomicLong = AtomicLong(0)

    @Volatile
    private var firstLogInBatchTime: AtomicLong = AtomicLong(0)

    @Volatile
    private var scheduledCheckFuture: ScheduledFuture<*>? = null

    init {
        sink.registerLogStoredCallback(::onLogsAdded)
    }

    override fun flush(saveOnly: Boolean) {
        scheduledCheckFuture?.cancel(false)
        scheduledCheckFuture = null
        firstLogInBatchTime.set(0)

        val envelope = logEnvelopeSource.getBatchedLogEnvelope()
        if (!envelope.data.logs.isNullOrEmpty()) {
            if (saveOnly) {
                deliveryService.saveLogs(envelope)
            } else {
                deliveryService.sendLogs(envelope)
            }
        }
    }

    private fun onLogsAdded() {
        logEnvelopeSource.getNonbatchedEnvelope().forEach { logEnvelope ->
            deliveryService.sendLogs(logEnvelope)
        }

        lastLogTime.set(clock.now())
        firstLogInBatchTime.compareAndSet(0, lastLogTime.get())
        if (!sendLogsIfNeeded()) {
            // If [firstLogInBatchTime] was cleared by a concurrent call to [sendLogsIfNeeded]
            // then update it to the time of this log
            firstLogInBatchTime.compareAndSet(0, lastLogTime.get())
            scheduleCheck()
        }
    }

    /**
     * Returns true if logs were sent, false otherwise
     */
    @Synchronized
    private fun sendLogsIfNeeded(): Boolean {
        val now = clock.now()
        val shouldSendLogs = isMaxLogsPerBatchReached() ||
            isMaxInactivityTimeReached(now) ||
            isMaxBatchTimeReached(now)

        if (!shouldSendLogs) {
            return false
        }
        flush(false)
        return true
    }

    private fun scheduleCheck() {
        val now = clock.now()
        val nextBatchCheck = MAX_BATCH_TIME - (now - firstLogInBatchTime.get())
        val nextInactivityCheck = MAX_INACTIVITY_TIME - (now - lastLogTime.get())
        scheduledCheckFuture?.cancel(false)
        scheduledCheckFuture = logOrchestratorScheduledWorker.schedule<Unit>(
            ::sendLogsIfNeeded,
            min(nextBatchCheck, nextInactivityCheck),
            TimeUnit.MILLISECONDS
        )
    }

    private fun isMaxLogsPerBatchReached(): Boolean =
        sink.completedLogs().size >= MAX_LOGS_PER_BATCH

    private fun isMaxInactivityTimeReached(now: Long): Boolean =
        now - lastLogTime.get() >= MAX_INACTIVITY_TIME

    private fun isMaxBatchTimeReached(now: Long): Boolean {
        val firstLogInBatchTime = firstLogInBatchTime.get()
        return firstLogInBatchTime != 0L && now - firstLogInBatchTime >= MAX_BATCH_TIME
    }

    companion object {
        const val MAX_LOGS_PER_BATCH = 50
        private const val MAX_BATCH_TIME = 5000L // In milliseconds
        private const val MAX_INACTIVITY_TIME = 2000L // In milliseconds
    }
}
