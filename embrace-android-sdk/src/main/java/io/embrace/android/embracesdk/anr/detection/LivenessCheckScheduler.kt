package io.embrace.android.embracesdk.anr.detection

import android.os.Message
import androidx.annotation.VisibleForTesting
import io.embrace.android.embracesdk.anr.detection.TargetThreadHandler.Companion.HEARTBEAT_REQUEST
import io.embrace.android.embracesdk.clock.Clock
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.enforceThread
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import java.util.concurrent.ScheduledExecutorService
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
    private val anrExecutor: ScheduledExecutorService,
    private val clock: Clock,
    private val state: ThreadMonitoringState,
    private val targetThreadHandler: TargetThreadHandler,
    private val blockedThreadDetector: BlockedThreadDetector,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger,
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
            logger.logInfo("Started heartbeats to capture ANRs.")
            scheduleRegularHeartbeats()
        }
    }

    /**
     * Stops monitoring the target thread.
     */
    fun stopMonitoringThread() {
        enforceThread(anrMonitorThread)

        if (state.started.get()) {
            monitorFuture?.let { monitorTask ->
                monitorTask.cancel(false)
                if (monitorTask.isDone) {
                    logger.logInfo("Stopped heartbeats to capture ANRs.")
                    state.started.set(false)
                } else {
                    logger.logError("Scheduled heartbeat could not be stopped.")
                }
            } ?: logger.logError(
                "Scheduled heartbeat could not be stopped. " +
                    "monitorFuture is null"
            )
        }
    }

    private fun scheduleRegularHeartbeats() {
        enforceThread(anrMonitorThread)

        intervalMs = configService.anrBehavior.getSamplingIntervalMs()
        val runnable = Runnable(::onMonitorThreadHeartbeat)
        try {
            logger.logDeveloper("EmbraceAnrService", "Heartbeat Interval: $intervalMs")
            monitorFuture =
                anrExecutor.scheduleAtFixedRate(runnable, 0, intervalMs, TimeUnit.MILLISECONDS)
        } catch (exc: Exception) {
            // ignore any RejectedExecution - ScheduledExecutorService only throws when shutting down.
            val message = "ANR capture initialization failed"
            logger.logError(message, exc, true)
        }
    }

    /**
     * Called at regular intervals on the monitor thread. This function posts a message to the
     * main thread that is used to check whether it is live or not.
     */
    @VisibleForTesting
    internal fun onMonitorThreadHeartbeat() {
        enforceThread(anrMonitorThread)

        try {
            with(configService.anrBehavior.getMonitorThreadPriority()) {
                android.os.Process.setThreadPriority(this)
            }

            if (intervalMs != configService.anrBehavior.getSamplingIntervalMs()) {
                logger.logDeveloper(
                    "EmbraceAnrService",
                    "Different interval detected, restarting runnable"
                )

                // we don't want to interrupt this Runnable while it's running
                monitorFuture?.cancel(false)

                // reschedule a heartbeat at the new cadence
                scheduleRegularHeartbeats()
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
            logger.logError(
                "Failed to send message to targetHandler, main thread likely shutting down.",
                IllegalStateException("Failed to send message to targetHandler"),
                true
            )
        }
    }
}
