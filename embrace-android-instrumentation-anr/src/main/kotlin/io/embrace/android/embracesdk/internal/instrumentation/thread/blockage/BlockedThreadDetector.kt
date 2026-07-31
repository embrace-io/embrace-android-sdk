package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Message
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Responsible for scheduling 'heartbeat' checks on a background thread & posting messages on the
 * target thread. If the target thread does not respond within a given time, a thread blockage is assumed.
 *
 * This class is responsible solely for the complicated logic of enqueuing a regular message on the
 * target thread & scheduling regular checks on a background thread. [BlockedThreadDetector]
 * is responsible for the business logic that checks whether a thread is blocked.
 */
class BlockedThreadDetector(
    private val watchdogWorker: BackgroundWorker,
    private val clock: Clock,
    private val looper: Looper,
    private val logger: InternalLogger,
    private val intervalMs: Long,
    private val blockedDurationThreshold: Int,
) {

    private val targetThreadHandler: TargetThreadHandler = TargetThreadHandler(
        looper = looper,
        watchdogWorker = watchdogWorker,
        clock = clock,
        action = ::onTargetThreadProcessedMessage,
    )
    private val started: AtomicBoolean = AtomicBoolean(false)
    private val blocked: AtomicBoolean = AtomicBoolean(false)
    private val lastWatchdogThreadResponse: AtomicLong = AtomicLong(clock.now())
    private val lastTargetThreadResponse: AtomicLong = AtomicLong(clock.now())

    /**
     * Everything that wants to know about blockages. Registration is rare and arrives on arbitrary
     * threads, while dispatch happens on the watchdog thread for as long as a blockage lasts, so a
     * copy-on-write list keeps dispatch cheap and lets a listener be added or removed while a blockage is
     * being reported.
     */
    private val listeners = CopyOnWriteArrayList<ThreadBlockageListener>()

    /**
     * The blockage reported to the listeners. One instance is reused for every callback, and for every
     * blockage, because listeners are not expected to hold onto it once their callback has returned.
     * Only ever read and written on the watchdog thread, so it needs no synchronization.
     *
     * This also holds when the current blockage began, which cannot be read from
     * [lastTargetThreadResponse] at report time because that is updated to the recovery time before the
     * end of a blockage is reported.
     */
    private val blockage = ThreadBlockage(
        startTimeMs = 0,
        lastKnownTimeMs = 0,
        thresholdMs = blockedDurationThreshold,
        pollIntervalMs = intervalMs,
    )
    private var monitorFuture: ScheduledFuture<*>? = null

    /**
     * Registers [listener] to receive blockages. Registering the same listener twice has no effect.
     */
    fun addListener(listener: ThreadBlockageListener) {
        listeners.addIfAbsent(listener)
    }

    /**
     * Stops [listener] receiving blockages.
     */
    fun removeListener(listener: ThreadBlockageListener) {
        listeners.remove(listener)
    }

    /**
     * Starts monitoring the target thread for blockages.
     */
    fun start() {
        if (!started.getAndSet(true)) {
            blocked.set(false)
            lastWatchdogThreadResponse.set(clock.now())
            lastTargetThreadResponse.set(clock.now())
            val runnable = Runnable(::checkHeartbeat)
            runCatching {
                monitorFuture = watchdogWorker.scheduleAtFixedRate(runnable, 0, intervalMs, TimeUnit.MILLISECONDS)
            }
        }
    }

    /**
     * Stops monitoring the target thread.
     */
    fun stop() {
        if (started.getAndSet(false)) {
            monitorFuture?.let { monitorTask ->
                if (monitorTask.cancel(false)) {
                    monitorFuture = null
                }
            }
        }
    }

    /**
     * Called when the target thread process the message. This indicates that the target thread is
     * responsive and (usually) means a thread blockage is about to end.
     */
    fun onTargetThreadProcessedMessage(timestamp: Long) {
        lastTargetThreadResponse.set(timestamp)

        if (isDebuggerEnabled()) {
            return
        }

        // thread was blocked but recovered
        if (blocked.getAndSet(false)) {
            blockage.lastKnownTimeMs = timestamp
            dispatch { it.onBlockageEnd(blockage) }
        }
    }

    /**
     * Called at regular intervals by the monitor thread. We should check whether the
     * target thread has been unresponsive.
     */
    fun onMonitorThreadInterval(timestamp: Long) {
        if (isDebuggerEnabled()) {
            return
        }

        if (isThreadBlockageThresholdExceeded(timestamp) && !blocked.getAndSet(true)) {
            blockage.startTimeMs = lastTargetThreadResponse.get()
            blockage.lastKnownTimeMs = timestamp
            dispatch { it.onBlockageStart(blockage) }
        }
        if (blocked.get() && shouldSampleBlockedThread(timestamp)) {
            blockage.lastKnownTimeMs = timestamp
            dispatch { it.onBlockageOngoing(blockage) }
        }
        lastWatchdogThreadResponse.set(clock.now())
    }

    /**
     * Reports the current [blockage] to every listener, isolating them from each other: one that throws
     * must not stop the rest from being told, or a bug in one feature becomes an outage in another.
     */
    private inline fun dispatch(action: (ThreadBlockageListener) -> Unit) {
        listeners.forEach { listener ->
            try {
                action(listener)
            } catch (exc: Exception) {
                logger.trackInternalError(InternalErrorType.ThreadBlockageListenerFail, exc)
            }
        }
    }

    /**
     * Called at regular intervals on the monitor thread. This function posts a message to the
     * main thread that is used to check whether it is live or not.
     */
    private fun checkHeartbeat() {
        try {
            val now = clock.now()
            if (!targetThreadHandler.hasMessages(HEARTBEAT_REQUEST)) {
                val heartbeatMessage = Message.obtain(targetThreadHandler, HEARTBEAT_REQUEST)
                targetThreadHandler.sendMessage(heartbeatMessage)
            }
            onMonitorThreadInterval(now)
        } catch (exc: Exception) {
            logger.trackInternalError(InternalErrorType.ThreadBlockageHeartbeatCheckFail, exc)
        }
    }

    /**
     * Decides whether we should attempt to sample the blocked thread. In ordinary conditions this
     * function will always return true. If the thread has been unable run due to priority then
     * several scheduled tasks may run in very quick succession of each other (e.g. 1ms apart).
     *
     * To avoid useless samples grouped within a few ms of each other, this function will return
     * false & thus avoid sampling if less than half of the interval MS has passed.
     */
    private fun shouldSampleBlockedThread(timestamp: Long): Boolean {
        val lastMonitorThreadResponseMs = lastWatchdogThreadResponse.get()
        val delta = timestamp - lastMonitorThreadResponseMs // time since last check
        return delta > intervalMs * SAMPLE_BACKOFF_FACTOR
    }

    /**
     * Checks whether enough time has elapsed for a thread to count as blocked
     */
    private fun isThreadBlockageThresholdExceeded(timestamp: Long): Boolean {
        val monitorThreadLag = timestamp - lastWatchdogThreadResponse.get()
        val targetThreadLag = timestamp - lastTargetThreadResponse.get()

        // If the last monitor thread check greatly exceeds the thread blockage threshold
        // then it is very probable that the process has been cached or frozen. In this case
        // we need to ignore the first heartbeat as the clock won't have been ticking
        // while the process was cached and this could cause a false positive.
        //
        // Therefore we reset the last response time from the target + monitor threads to
        // the current time so that we can start monitoring for thread blockages again.
        // https://developer.android.com/guide/components/activities/process-lifecycle
        if (monitorThreadLag > MONITOR_THREAD_TIMEOUT_MS) {
            val now = clock.now()
            lastTargetThreadResponse.set(now)
            lastWatchdogThreadResponse.set(now)
            return false
        }
        return targetThreadLag > blockedDurationThreshold
    }

    /**
     * Returns true if the debugger is enabled - as we want to eliminate false positive thread blockages.
     */
    private fun isDebuggerEnabled(): Boolean =
        Debug.isDebuggerConnected() || Debug.waitingForDebugger()

    /**
     * A [Handler] that processes messages enqueued on the target [Looper]. If a message is not
     * processed by this class in a timely manner then it indicates the target thread is blocked
     * with too much work. Once this class has processed the message, the thread blockage is marked as finished.
     */
    private class TargetThreadHandler(
        looper: Looper,
        private val watchdogWorker: BackgroundWorker,
        private val clock: Clock,
        private val action: (time: Long) -> Unit,
    ) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            runCatching {
                if (msg.what == HEARTBEAT_REQUEST) {
                    val timestamp = clock.now()
                    watchdogWorker.submit {
                        action(timestamp)
                    }
                }
            }
        }
    }

    private companion object {

        /**
         * The number of milliseconds which the monitor thread is allowed to timeout before we
         * assume the process is in a cached state.
         */
        const val MONITOR_THREAD_TIMEOUT_MS = 60000L

        /**
         * The % of a regular interval that must have elapsed for us to consider taking another sample.
         *
         * This helps avoid two scenarios: performing too much work and contributing to thread blockages, and
         * taking many samples within a few ms of each other (when the monitor thread has not been
         * scheduled enough CPU time).
         */
        const val SAMPLE_BACKOFF_FACTOR = 0.5

        /**
         * Unique ID for Handler message (arbitrary number).
         */
        const val HEARTBEAT_REQUEST: Int = 34593
    }
}
