package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.arch.stacktrace.ThreadSample
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.anr.ThreadBlockageSample.Companion.CODE_DEFAULT
import io.embrace.android.embracesdk.internal.instrumentation.anr.ThreadBlockageSample.Companion.CODE_SAMPLE_LIMIT_REACHED
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent.BLOCKED
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent.BLOCKED_INTERVAL
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent.UNBLOCKED
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageListener
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadStacktraceSampler
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
    private val maxSamplesPerInterval: Int,
    private val stacktraceFrameLimit: Int,
) : ThreadBlockageListener, MemoryCleanerListener {

    private val intervals = CopyOnWriteArrayList<ThreadBlockageInterval>()
    private val sampler = AtomicReference<ThreadStacktraceSampler>()
    private val lastUnblockedMs: AtomicLong = AtomicLong(0)
    private val blocked = AtomicBoolean(false)

    override fun onThreadBlockageEvent(
        event: ThreadBlockageEvent,
        timestamp: Long,
    ) {
        blocked.set(event != UNBLOCKED)
        when (event) {
            BLOCKED -> onThreadBlocked(timestamp)
            BLOCKED_INTERVAL -> sampler.get().captureSample()
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
                        samples = sampler.get().getThreadBlockageSamples(),
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
        resetState(timestamp)
    }

    private fun onThreadUnblocked(timestamp: Long) {
        synchronized(intervals) {
            if (intervals.size < MAX_INTERVAL_COUNT) {
                intervals.add(
                    ThreadBlockageInterval(
                        startTime = lastUnblockedMs.get(),
                        endTime = timestamp,
                        samples = sampler.get().getThreadBlockageSamples(),
                    )
                )

                while (reachedIntervalCaptureLimit()) {
                    findLeastValuableIntervalWithSamples()?.let { entry ->
                        val index = intervals.indexOf(entry)
                        intervals.remove(entry)
                        intervals.add(index, entry.clearSamples())
                    }
                }
            }
        }
        resetState(timestamp)
    }

    private fun ThreadStacktraceSampler.getThreadBlockageSamples(): List<ThreadBlockageSample> {
        val metadata = retrieveSampleMetadata()
        var lastSample: ThreadSample? = null

        return metadata.map {
            val current = it.sample

            // if a stacktrace is repeated in consecutive elements, omit it from the payload. This
            // is done for legacy reasons with the aim of saving payload size.
            val dedupe = when (lastSample) {
                current -> null
                else -> current
            }
            lastSample = current

            ThreadBlockageSample(
                timestamp = it.sampleTimeMs,
                threadSample = dedupe,
                sampleOverheadMs = it.sampleOverheadMs,
                code = when (current) {
                    null -> CODE_SAMPLE_LIMIT_REACHED
                    else -> CODE_DEFAULT
                }
            )
        }
    }

    private fun resetState(timestamp: Long) {
        sampler.set(ThreadStacktraceSampler(clock, targetThread, maxSamplesPerInterval, stacktraceFrameLimit))
        lastUnblockedMs.set(timestamp)
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

    private companion object {

        /**
         * Hard limit for the maximum number of intervals an SDK wants to send in a payload.
         * Not all of these intervals will have stacktrace samples associated with them
         * (that is set via a configurable limit).
         */
        private const val MAX_INTERVAL_COUNT = 100
    }
}
