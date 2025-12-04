package io.embrace.android.embracesdk.internal.instrumentation.anr.detection

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.MessageQueue
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * The number of milliseconds which the monitor thread is allowed to timeout before we
 * assume that the process has been put into the cached state.
 *
 * All functions in this class MUST be called from the same thread - this is part of the
 * synchronization strategy that ensures ANR data is not corrupted.
 */
private const val MONITOR_THREAD_TIMEOUT_MS = 60000L

/**
 * The % of a regular interval that must have elapsed for us to consider taking another sample.
 *
 * This helps avoid two scenarios: performing too much work and contributing to ANRs, and
 * taking many samples within a few ms of each other (when the monitor thread has not been
 * scheduled enough CPU time).
 */
private const val SAMPLE_BACKOFF_FACTOR = 0.5

/**
 * Unique ID for Handler message (arbitrary number).
 */
internal const val HEARTBEAT_REQUEST: Int = 34593

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
    private val anrMonitorWorker: BackgroundWorker,
    private val clock: Clock,
    private val state: ThreadMonitoringState,
    private val targetThreadHandler: TargetThreadHandler,
    private val blockedThreadDetector: BlockedThreadDetector,
    private var intervalMs: Long,
    private val logger: EmbLogger,
) {

    var listener: BlockedThreadListener?
        set(value) {
            blockedThreadDetector.listener = value
        }
        get() = blockedThreadDetector.listener

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

    private fun scheduleRegularHeartbeats() {
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
            val now = clock.now()
            if (!targetThreadHandler.hasMessages(HEARTBEAT_REQUEST)) {
                sendHeartbeatMessage()
            }
            blockedThreadDetector.updateAnrTracking(now)
        } catch (exc: Exception) {
            logger.trackInternalError(InternalErrorType.ANR_HEARTBEAT_CHECK_FAIL, exc)
        }
    }

    private fun sendHeartbeatMessage() {
        val heartbeatMessage = Message.obtain(targetThreadHandler, HEARTBEAT_REQUEST)
        targetThreadHandler.sendMessage(heartbeatMessage)
    }
}

/**
 * A [Handler] that processes messages enqueued on the target [Looper]. If a message is not
 * processed by this class in a timely manner then it indicates the target thread is blocked
 * with too much work.
 *
 * When this class processes the message it submits the [action] for execution on the supplied
 * [ExecutorService].
 *
 * Basically speaking: if [handleMessage] takes a long time, the monitor thread assumes there is
 * an ANR after a certain time threshold. Once [handleMessage] is invoked, the monitor thread
 * knows for sure that the target thread is responsive, so resets the timer for any ANRs.
 */
internal class TargetThreadHandler(
    looper: Looper,
    private val anrMonitorWorker: BackgroundWorker,
    private val messageQueue: MessageQueue? = LooperCompat.getMessageQueue(looper),
    private val clock: Clock,
) : Handler(looper) {

    lateinit var action: (time: Long) -> Unit

    @Volatile
    var installed: Boolean = false

    fun onIdleThread(): Boolean {
        onMainThreadUnblocked()
        return true
    }

    override fun handleMessage(msg: Message) {
        runCatching {
            if (msg.what == HEARTBEAT_REQUEST) {
                // We couldn't obtain the target thread message queue. This should not happen,
                // but if it does then we just log an internal error & consider the ANR ended at
                // this point.
                if (messageQueue == null || !installed) {
                    onMainThreadUnblocked()
                }
            }
        }
    }

    private fun onMainThreadUnblocked() {
        val timestamp = clock.now()
        anrMonitorWorker.submit {
            action.invoke(timestamp)
        }
    }
}

/**
 * Responsible for deciding whether a thread is blocked or not. The actual scheduling happens in
 * [LivenessCheckScheduler] whereas this class contains the business logic.
 */
class BlockedThreadDetector(
    private val clock: Clock,
    var listener: BlockedThreadListener? = null,
    private val state: ThreadMonitoringState,
    private val targetThread: Thread,
    val blockedDurationThreshold: Int,
    val samplingIntervalMs: Long,
) {

    /**
     * Called when the target thread process the message. This indicates that the target thread is
     * responsive and (usually) means an ANR is about to end.
     *
     * All functions in this class MUST be called from the same thread - this is part of the
     * synchronization strategy that ensures ANR data is not corrupted.
     */
    fun onTargetThreadResponse(timestamp: Long) {
        state.lastTargetThreadResponseMs = timestamp

        if (isDebuggerEnabled()) {
            return
        }

        if (state.anrInProgress) {
            // Application was not responding, but recovered
            // Invoke callbacks
            state.anrInProgress = false
            listener?.onThreadUnblocked(targetThread, timestamp)
        }
    }

    /**
     * Called at regular intervals by the monitor thread. We should check whether the
     * target thread has been unresponsive & decide whether this means an ANR is happening.
     *
     * All functions in this class MUST be called from the same thread - this is part of the
     * synchronization strategy that ensures ANR data is not corrupted.
     */
    fun updateAnrTracking(timestamp: Long) {
        if (isDebuggerEnabled()) {
            return
        }

        if (!state.anrInProgress && isAnrDurationThresholdExceeded(timestamp)) {
            state.anrInProgress = true
            listener?.onThreadBlocked(targetThread, state.lastTargetThreadResponseMs)
        }
        if (state.anrInProgress && shouldAttemptAnrSample(timestamp)) {
            listener?.onThreadBlockedInterval(
                targetThread,
                timestamp
            )
            state.lastSampleAttemptMs = clock.now()
        }
        state.lastMonitorThreadResponseMs = clock.now()
    }

    /**
     * Decides whether we should attempt an ANR sample or not. In ordinary conditions this
     * function will always return true. If the thread has been unable run due to priority then
     * several scheduled tasks may run in very quick succession of each other (e.g. 1ms apart).
     *
     * To avoid useless samples grouped within a few ms of each other, this function will return
     * false & thus avoid sampling if less than half of the interval MS has passed.
     */
    fun shouldAttemptAnrSample(timestamp: Long): Boolean {
        val lastMonitorThreadResponseMs = state.lastMonitorThreadResponseMs
        val delta = timestamp - lastMonitorThreadResponseMs // time since last check
        return delta > samplingIntervalMs * SAMPLE_BACKOFF_FACTOR
    }

    /**
     * Checks whether the ANR duration threshold has been exceeded or not.
     *
     * This defaults to the main thread not having processed a message within 1s.
     */

    fun isAnrDurationThresholdExceeded(timestamp: Long): Boolean {
        val monitorThreadLag = timestamp - state.lastMonitorThreadResponseMs
        val targetThreadLag = timestamp - state.lastTargetThreadResponseMs

        // If the last monitor thread check greatly exceeds the ANR threshold
        // then it is very probable that the process has been cached or frozen. In this case
        // we need to ignore the first heartbeat as the clock won't have been ticking
        // while the process was cached and this could cause a false positive.
        //
        // Therefore we reset the last response time from the target + monitor threads to
        // the current time so that we can start monitoring for ANRs again.
        // https://developer.android.com/guide/components/activities/process-lifecycle
        if (monitorThreadLag > MONITOR_THREAD_TIMEOUT_MS) {
            val now = clock.now()
            state.lastTargetThreadResponseMs = now
            state.lastMonitorThreadResponseMs = now
            return false
        }
        return targetThreadLag > blockedDurationThreshold
    }

    /**
     * Returns true if the debugger is enabled - as we want to eliminate false positive ANRs.
     */
    private fun isDebuggerEnabled(): Boolean =
        Debug.isDebuggerConnected() || Debug.waitingForDebugger()
}
