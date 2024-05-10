package io.embrace.android.embracesdk.anr.detection

import android.os.Message
import io.embrace.android.embracesdk.anr.detection.TargetThreadHandler.Companion.HEARTBEAT_REQUEST
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.enforceThread
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.ResponsivenessSnapshot
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
internal class LivenessCheckScheduler internal constructor(
    configService: ConfigService,
    private val anrMonitorWorker: ScheduledWorker,
    private val clock: Clock,
    private val state: ThreadMonitoringState,
    private val targetThreadHandler: TargetThreadHandler,
    private val blockedThreadDetector: BlockedThreadDetector,
    private val logger: EmbLogger,
    private val anrMonitorThread: AtomicReference<Thread>
) {

    var configService
        set(value) {
            blockedThreadDetector.configService = value
        }
        get() = blockedThreadDetector.configService

    var listener
        set(value) {
            blockedThreadDetector.listener = value
        }
        get() = blockedThreadDetector.listener

    private val heartbeatSendMonitor = ResponsivenessMonitor(clock = clock, name = "heartbeatSend")
    private var intervalMs: Long = configService.anrBehavior.getSamplingIntervalMs()
    private var monitorFuture: ScheduledFuture<*>? = null

    init {
        targetThreadHandler.action = blockedThreadDetector::onTargetThreadResponse
        targetThreadHandler.start()
    }

    /**
     * Starts monitoring the target thread for blockages.
     */
    fun startMonitoringThread() {
        enforceThread(anrMonitorThread)
        if (!state.started.getAndSet(true)) {
            logger.logInfo("Start ANR detection...")
            resetResponsivenessMonitors()
            scheduleRegularHeartbeats()
        }
    }

    /**
     * Stops monitoring the target thread.
     */
    fun stopMonitoringThread() {
        enforceThread(anrMonitorThread)
        if (state.started.get()) {
            if (stopHeartbeatTask()) {
                state.started.set(false)
            }
        }
    }

    fun resetResponsivenessMonitors() {
        heartbeatSendMonitor.reset()
        blockedThreadDetector.resetResponsivenessMonitor()
    }

    fun responsivenessMonitorSnapshots(): List<ResponsivenessSnapshot> =
        listOf(heartbeatSendMonitor.snapshot(), blockedThreadDetector.responsivenessMonitorSnapshot())

    @Suppress("DEPRECATION")
    private fun scheduleRegularHeartbeats() {
        enforceThread(anrMonitorThread)

        intervalMs = configService.anrBehavior.getSamplingIntervalMs()
        val runnable = Runnable(::checkHeartbeat)
        try {
            logger.logInfo("Starting ANR heartbeats with interval: ${intervalMs}ms")
            monitorFuture = anrMonitorWorker.scheduleAtFixedRate(runnable, 0, intervalMs, TimeUnit.MILLISECONDS)
        } catch (exc: Exception) {
            // ignore any RejectedExecution - ScheduledExecutorService only throws when shutting down.
            val message = "ANR capture initialization failed"
            logger.logWarning(message, exc, true)
        }
    }

    private fun stopHeartbeatTask(): Boolean {
        enforceThread(anrMonitorThread)

        monitorFuture?.let { monitorTask ->
            if (monitorTask.cancel(false)) {
                logger.logInfo("Stopped ANR detection...")
                monitorFuture = null
                return true
            }
        }
        // There is no expected situation where this cancel should fail because monitorFuture should never be null or canceled when
        // we get here, and if this is running, the heartbeat task won't be. If for some reason it fails, log an error.
        val message = "Scheduled heartbeat task could not be stopped." + if (monitorFuture == null) "Task is null." else ""
        logger.logError(message, IllegalStateException(message))
        return false
    }

    /**
     * Called at regular intervals on the monitor thread. This function posts a message to the
     * main thread that is used to check whether it is live or not.
     */
    internal fun checkHeartbeat() {
        enforceThread(anrMonitorThread)

        try {
            heartbeatSendMonitor.ping()
            with(configService.anrBehavior.getMonitorThreadPriority()) {
                android.os.Process.setThreadPriority(this)
            }

            if (intervalMs != configService.anrBehavior.getSamplingIntervalMs()) {
                logger.logInfo("Different interval detected, scheduling a heartbeat restart")
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
            logger.logError("Failed to process ANR monitor thread heartbeat", exc, true)
        }
    }

    private fun sendHeartbeatMessage() {
        val heartbeatMessage = Message.obtain(targetThreadHandler, HEARTBEAT_REQUEST)
        if (!targetThreadHandler.sendMessage(heartbeatMessage)) {
            logger.logWarning(
                "Failed to send message to targetHandler, main thread likely shutting down.",
                IllegalStateException("Failed to send message to targetHandler"),
                true
            )
        }
    }
}
