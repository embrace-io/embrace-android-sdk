package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.arch.stacktrace.ThreadSample
import io.embrace.android.embracesdk.internal.arch.stacktrace.truncateStacktrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageEvent
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageListener
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.ThreadBlockageInterval
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.ThreadBlockageSample
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.CopyOnWriteArrayList

/**
 * This class is responsible for tracking the state of JVM stacktraces sampled during an ANR.
 */
internal class AnrStacktraceSampler(
    private val clock: Clock,
    private val state: ThreadMonitoringState,
    private val targetThread: Thread,
    private val watchdogWorker: BackgroundWorker,
    private val maxIntervalsPerSession: Int,
    private val maxStacktracesPerInterval: Int,
    private val stacktraceFrameLimit: Int,
) : ThreadBlockageListener, MemoryCleanerListener {

    private val threadBlockageIntervals: CopyOnWriteArrayList<ThreadBlockageInterval> =
        CopyOnWriteArrayList<ThreadBlockageInterval>()
    private val samples = mutableListOf<ThreadBlockageSample>()
    private val memo: MutableMap<Long, ThreadSample> = mutableMapOf()
    private var lastUnblockedMs: Long = 0

    override fun onThreadBlockageEvent(
        event: ThreadBlockageEvent,
        timestamp: Long,
    ) {
        when (event) {
            ThreadBlockageEvent.BLOCKED -> onThreadBlocked(timestamp)
            ThreadBlockageEvent.BLOCKED_INTERVAL -> onThreadBlockedInterval(timestamp)
            ThreadBlockageEvent.UNBLOCKED -> onThreadUnblocked(timestamp)
        }
    }

    /**
     * Retrieves ANR intervals that match the given start/time windows.
     */
    fun getAnrIntervals(): List<ThreadBlockageInterval> {
        synchronized(threadBlockageIntervals) {
            val results = threadBlockageIntervals.toMutableList()

            // add any in-progress ANRs
            if (state.threadBlockageInProgress) {
                val intervalEndTime = clock.now()
                val responseMs = state.lastTargetThreadResponseMs
                val threadBlockageInterval = ThreadBlockageInterval(
                    responseMs,
                    intervalEndTime,
                    null,
                    samples.toList()
                )
                results.add(threadBlockageInterval)
            }
            return results.map(ThreadBlockageInterval::deepCopy)
        }
    }

    override fun cleanCollections() {
        watchdogWorker.submit {
            threadBlockageIntervals.removeAll { it.endTime != null }
        }
    }

    private fun onThreadBlocked(timestamp: Long) {
        memo.clear()
        lastUnblockedMs = timestamp
    }

    private fun onThreadBlockedInterval(timestamp: Long) {
        val limit = maxStacktracesPerInterval
        val threadBlockageSample = if (samples.size >= limit) {
            ThreadBlockageSample(timestamp, null, 0, ThreadBlockageSample.CODE_SAMPLE_LIMIT_REACHED)
        } else {
            val start = clock.now()
            val threads = captureSample()
            val sampleOverheadMs = clock.now() - start
            ThreadBlockageSample(timestamp, threads, sampleOverheadMs)
        }
        samples.add(threadBlockageSample)
    }

    private fun onThreadUnblocked(timestamp: Long) {
        // Finalize AnrInterval
        val responseMs = lastUnblockedMs
        val sanitizedSamples = samples.filter { it.timestamp in responseMs..timestamp }
        val threadBlockageInterval = ThreadBlockageInterval(
            responseMs,
            null,
            timestamp,
            sanitizedSamples
        )

        synchronized(threadBlockageIntervals) {
            if (threadBlockageIntervals.size < MAX_INTERVAL_COUNT) {
                threadBlockageIntervals.add(threadBlockageInterval)

                while (reachedAnrStacktraceCaptureLimit()) {
                    findLeastValuableIntervalWithSamples()?.let { entry ->
                        val index = threadBlockageIntervals.indexOf(entry)
                        threadBlockageIntervals.remove(entry)
                        threadBlockageIntervals.add(index, entry.clearSamples())
                    }
                }
            }
        }

        // reset state
        samples.clear()
        lastUnblockedMs = timestamp
        memo.clear()
    }

    /**
     * Finds the 'least valuable' ANR interval. This is used when the maximum number of ANR
     * intervals with samples has been reached & the SDK needs to discard samples. We attempt
     * to pick the least valuable interval in this case.
     */
    private fun findLeastValuableIntervalWithSamples(): ThreadBlockageInterval? =
        findIntervalsWithSamples().minByOrNull(ThreadBlockageInterval::duration)

    private fun reachedAnrStacktraceCaptureLimit(): Boolean {
        val count = findIntervalsWithSamples().size
        return count > maxIntervalsPerSession
    }

    private fun findIntervalsWithSamples() = threadBlockageIntervals.filter(ThreadBlockageInterval::hasSamples)

    /**
     * Captures the thread traces required for the given sample.
     */
    private fun captureSample(): ThreadSample? {
        val capturedTrace = truncateStacktrace(
            targetThread,
            targetThread.stackTrace,
            stacktraceFrameLimit
        )

        // Compares main thread with the last known thread state via hashcode. If hashcode changed
        // it should be added to the anrInfo list and also the currentAnrInfoState must be updated.
        val threadId = capturedTrace.threadId
        val cache: ThreadSample? = memo[threadId]

        // only serialize if the previous stacktrace doesn't match.
        if (cache == null || capturedTrace != cache) {
            memo[threadId] = capturedTrace
            return capturedTrace
        }
        return null
    }

    private companion object {

        /**
         * Hard limit for the maximum number of ANR intervals an SDK wants to send in a payload.
         * Not all of these intervals will have stacktrace samples associated with them
         * (that is set via a configurable limit).
         */
        private const val MAX_INTERVAL_COUNT = 100
    }
}
