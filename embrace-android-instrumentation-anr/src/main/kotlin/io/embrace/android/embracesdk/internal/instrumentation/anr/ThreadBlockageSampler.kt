package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.arch.stacktrace.ThreadSample
import io.embrace.android.embracesdk.internal.arch.stacktrace.truncateStacktrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent.BLOCKED
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent.BLOCKED_INTERVAL
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent.UNBLOCKED
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageListener
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * This class is responsible for tracking the state of JVM stacktraces sampled during a thread blockage.
 */
internal class ThreadBlockageSampler(
    private val clock: Clock,
    private val targetThread: Thread,
    private val maxIntervalsPerSession: Int,
    private val maxStacktracesPerInterval: Int,
    private val stacktraceFrameLimit: Int,
) : ThreadBlockageListener, MemoryCleanerListener {

    private val intervals = CopyOnWriteArrayList<ThreadBlockageInterval>()
    private val samples = CopyOnWriteArrayList<ThreadBlockageSample>()
    private val prevSample: AtomicReference<ThreadSample?> = AtomicReference(null)
    private val lastUnblockedMs: AtomicLong = AtomicLong(0)
    private val blocked = AtomicBoolean(false)

    override fun onThreadBlockageEvent(
        event: ThreadBlockageEvent,
        timestamp: Long,
    ) {
        blocked.set(event != UNBLOCKED)
        when (event) {
            BLOCKED -> onThreadBlocked(timestamp)
            BLOCKED_INTERVAL -> onThreadBlockedInterval(timestamp)
            UNBLOCKED -> onThreadUnblocked(timestamp)
        }
    }

    /**
     * Retrieves thread blockage intervals for the current session
     */
    fun getThreadBlockageIntervals(): List<ThreadBlockageInterval> {
        synchronized(intervals) {
            val results = intervals.toMutableList()

            // add any in-progress intervals
            if (blocked.get()) {
                results.add(
                    ThreadBlockageInterval(
                        startTime = lastUnblockedMs.get(),
                        lastKnownTime = clock.now(),
                        samples = samples.toList()
                    )
                )
            }
            return results.map(ThreadBlockageInterval::deepCopy)
        }
    }

    override fun cleanCollections() {
        synchronized(intervals) {
            intervals.removeAll { it.endTime != null }
        }
    }

    private fun onThreadBlocked(timestamp: Long) {
        prevSample.set(null)
        lastUnblockedMs.set(timestamp)
    }

    private fun onThreadBlockedInterval(timestamp: Long) {
        val start = clock.now()
        val sampleLimitExceeded = samples.size >= maxStacktracesPerInterval

        val threads = when {
            sampleLimitExceeded -> null
            else -> captureSample()
        }
        val code = when {
            sampleLimitExceeded -> ThreadBlockageSample.CODE_SAMPLE_LIMIT_REACHED
            else -> ThreadBlockageSample.CODE_DEFAULT
        }
        val sampleOverheadMs = when {
            sampleLimitExceeded -> 0
            else -> clock.now() - start
        }

        samples.add(
            ThreadBlockageSample(
                timestamp = timestamp,
                threadSample = threads,
                sampleOverheadMs = sampleOverheadMs,
                code = code,
            )
        )
    }

    private fun onThreadUnblocked(timestamp: Long) {
        val responseMs = lastUnblockedMs.get()
        val sanitizedSamples = samples.filter { it.timestamp in responseMs..timestamp }
        val threadBlockageInterval = ThreadBlockageInterval(
            startTime = responseMs,
            endTime = timestamp,
            samples = sanitizedSamples
        )

        synchronized(intervals) {
            if (intervals.size < MAX_INTERVAL_COUNT) {
                intervals.add(threadBlockageInterval)

                while (reachedIntervalCaptureLimit()) {
                    findLeastValuableIntervalWithSamples()?.let { entry ->
                        val index = intervals.indexOf(entry)
                        intervals.remove(entry)
                        intervals.add(index, entry.clearSamples())
                    }
                }
            }
        }

        // reset state
        samples.clear()
        lastUnblockedMs.set(timestamp)
        prevSample.set(null)
    }

    /**
     * Finds the 'least valuable' interval. This is used when the maximum number of
     * intervals with samples has been reached & the SDK needs to discard samples. We attempt
     * to pick the least valuable interval in this case.
     */
    private fun findLeastValuableIntervalWithSamples(): ThreadBlockageInterval? {
        return intervals.filter(ThreadBlockageInterval::hasSamples).minByOrNull(ThreadBlockageInterval::duration)
    }

    private fun reachedIntervalCaptureLimit(): Boolean {
        val count = intervals.filter(ThreadBlockageInterval::hasSamples).size
        return count > maxIntervalsPerSession
    }

    /**
     * Captures the thread traces required for the given sample.
     */
    private fun captureSample(): ThreadSample? {
        val sample = truncateStacktrace(
            targetThread,
            targetThread.stackTrace,
            stacktraceFrameLimit
        )

        // Compares main thread with the last known thread state via hashcode.
        // Only serialize if the previous stacktrace doesn't match.
        if (sample != prevSample.get()) {
            prevSample.set(sample)
            return sample
        }
        return null
    }

    private companion object {

        /**
         * Hard limit for the maximum number of intervals an SDK wants to send in a payload.
         * Not all of these intervals will have stacktrace samples associated with them
         * (that is set via a configurable limit).
         */
        private const val MAX_INTERVAL_COUNT = 100
    }
}
