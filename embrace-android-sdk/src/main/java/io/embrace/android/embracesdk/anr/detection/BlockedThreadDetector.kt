package io.embrace.android.embracesdk.anr.detection

import android.os.Debug
import io.embrace.android.embracesdk.anr.BlockedThreadListener
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.enforceThread
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import java.util.concurrent.atomic.AtomicReference

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
 * Responsible for deciding whether a thread is blocked or not. The actual scheduling happens in
 * [LivenessCheckScheduler] whereas this class contains the business logic.
 */
internal class BlockedThreadDetector constructor(
    var configService: ConfigService,
    private val clock: Clock,
    var listener: BlockedThreadListener? = null,
    private val state: ThreadMonitoringState,
    private val targetThread: Thread,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger,
    private val anrMonitorThread: AtomicReference<Thread>
) {

    /**
     * Called when the target thread process the message. This indicates that the target thread is
     * responsive and (usually) means an ANR is about to end.
     *
     * All functions in this class MUST be called from the same thread - this is part of the
     * synchronization strategy that ensures ANR data is not corrupted.
     */
    fun onTargetThreadResponse(timestamp: Long) {
        enforceThread(anrMonitorThread)

        state.lastTargetThreadResponseMs = timestamp

        if (isDebuggerEnabled()) {
            return
        }

        if (state.anrInProgress) {
            // Application was not responding, but recovered
            logger.logDebug("Main thread recovered from not responding for > 1s")

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
        enforceThread(anrMonitorThread)

        if (isDebuggerEnabled()) {
            return
        }

        if (!state.anrInProgress && isAnrDurationThresholdExceeded(timestamp)) {
            logger.logDebug("Main thread not responding for > 1s")
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

    internal fun shouldAttemptAnrSample(timestamp: Long): Boolean {
        val lastMonitorThreadResponseMs = state.lastMonitorThreadResponseMs
        val delta = timestamp - lastMonitorThreadResponseMs // time since last check
        val intervalMs = configService.anrBehavior.getSamplingIntervalMs()
        return delta > intervalMs * SAMPLE_BACKOFF_FACTOR
    }

    /**
     * Checks whether the ANR duration threshold has been exceeded or not.
     *
     * This defaults to the main thread not having processed a message within 1s.
     */

    internal fun isAnrDurationThresholdExceeded(timestamp: Long): Boolean {
        enforceThread(anrMonitorThread)

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
            logger.logDeveloper("EmbraceAnrService", "Exceeded monitor thread timeout")
            val now = clock.now()
            state.lastTargetThreadResponseMs = now
            state.lastMonitorThreadResponseMs = now
            return false
        }
        val minTriggerDuration = configService.anrBehavior.getMinDuration()
        return targetThreadLag > minTriggerDuration
    }

    /**
     * Returns true if the debugger is enabled - as we want to eliminate false positive ANRs.
     */
    private fun isDebuggerEnabled(): Boolean =
        Debug.isDebuggerConnected() || Debug.waitingForDebugger()
}
