package io.embrace.android.embracesdk.internal.anr.detection

import android.os.Message
import io.embrace.android.embracesdk.internal.anr.BlockedThreadListener
import io.embrace.android.embracesdk.internal.anr.detection.TargetThreadHandler.Companion.HEARTBEAT_REQUEST
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Responsible for scheduling 'heartbeat' checks on a background thread & posting messages on the
 * target thread.
 *
 * If the target thread does not respond within a given time, an ANR is assumed to have happened.
 *
 * This class is responsible solely for the complicated logic of enqueuing a regular message on the
 * target thread & scheduling regular checks on a background thread. The [BlockedThreadDetector]
 * class is responsible for the actual business logic that checks whether a thread is blocked or not.
 */
internal class LivenessCheckScheduler(
    configService: ConfigService,
    private val anrMonitorWorker: BackgroundWorker,
    private val clock: Clock,
    private val state: ThreadMonitoringState,
    private val targetThreadHandler: TargetThreadHandler,
    private val blockedThreadDetector: BlockedThreadDetector,
    private val logger: EmbLogger,
) {

    var configService: ConfigService
        set(value) {
            blockedThreadDetector.configService = value
        }
        get() = blockedThreadDetector.configService

    var listener: BlockedThreadListener?
        set(value) {
            blockedThreadDetector.listener = value
        }
        get() = blockedThreadDetector.listener

    private var intervalMs: Long = configService.anrBehavior.getSamplingIntervalMs()
    private var monitorFuture: ScheduledFuture<*>? = null

    init {
        targetThreadHandler.action = blockedThreadDetector::onTargetThreadResponse
    }

    /**
     * Starts monitoring the target thread for blockages.
     */
    fun startMonitoringThread() {
        if (!state.started.getAndSet(true)) {
            scheduleRegularHeartbeats()
        }
    }

    /**
     * Stops monitoring the target thread.
     */
    fun stopMonitoringThread() {
        if (state.started.get()) {
            if (stopHeartbeatTask()) {
                state.started.set(false)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun scheduleRegularHeartbeats() {
        intervalMs = configService.anrBehavior.getSamplingIntervalMs()
        val runnable = Runnable(::checkHeartbeat)
        runCatching {
            monitorFuture = anrMonitorWorker.scheduleAtFixedRate(runnable, 0, intervalMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun stopHeartbeatTask(): Boolean {
        monitorFuture?.let { monitorTask ->
            if (monitorTask.cancel(false)) {
                monitorFuture = null
                return true
            }
        }
        // There is no expected situation where this cancel should fail because monitorFuture should never be null or canceled when
        // we get here, and if this is running, the heartbeat task won't be. If for some reason it fails, log an error.
        val message =
            "Scheduled heartbeat task could not be stopped." + if (monitorFuture == null) "Task is null." else ""
        val exc = IllegalStateException(message)
        logger.trackInternalError(InternalErrorType.ANR_HEARTBEAT_STOP_FAIL, exc)
        return false
    }

    /**
     * Called at regular intervals on the monitor thread. This function posts a message to the
     * main thread that is used to check whether it is live or not.
     */
    fun checkHeartbeat() {
        try {
            if (intervalMs != configService.anrBehavior.getSamplingIntervalMs()) {
                // Different interval detected, scheduling a heartbeat restart
                anrMonitorWorker.submit {
                    if (stopHeartbeatTask()) {
                        scheduleRegularHeartbeats()
                    }
                }
            } else {
                val now = clock.now()
                if (!targetThreadHandler.hasMessages(HEARTBEAT_REQUEST)) {
                    sendHeartbeatMessage()
                }
                blockedThreadDetector.updateAnrTracking(now)
            }
        } catch (exc: Exception) {
            logger.trackInternalError(InternalErrorType.ANR_HEARTBEAT_CHECK_FAIL, exc)
        }
    }

    private fun sendHeartbeatMessage() {
        val heartbeatMessage = Message.obtain(targetThreadHandler, HEARTBEAT_REQUEST)
        targetThreadHandler.sendMessage(heartbeatMessage)
    }
}
