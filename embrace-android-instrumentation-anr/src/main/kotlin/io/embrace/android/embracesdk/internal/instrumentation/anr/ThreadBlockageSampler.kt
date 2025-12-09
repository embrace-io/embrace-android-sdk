package io.embrace.android.embracesdk.internal.instrumentation.anr

import androidx.annotation.CheckResult
import io.embrace.android.embracesdk.internal.arch.stacktrace.ThreadSample
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.anr.ThreadBlockageInterval.Companion.CODE_SAMPLES_CLEARED
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

    private val intervalSink = AtomicReference(CopyOnWriteArrayList<ThreadBlockageInterval>())
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
        val intervals = intervalSink.get()
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

        while (reachedIntervalCaptureLimit(results)) {
            findLeastValuableIntervalWithSamples(results)?.let { entry ->
                val index = results.indexOf(entry)
                results.remove(entry)
                results.add(index, entry.clearSamples())
            }
        }
        return results
    }

    override fun cleanCollections() {
        intervalSink.set(CopyOnWriteArrayList(intervalSink.get().filter { it.endTime == null }))
    }

    private fun onThreadBlocked(timestamp: Long) {
        resetState(timestamp)
    }

    private fun onThreadUnblocked(timestamp: Long) {
        val intervals = intervalSink.get()
        if (intervals.size < MAX_INTERVAL_COUNT) {
            intervals.add(
                ThreadBlockageInterval(
                    startTime = lastUnblockedMs.get(),
                    endTime = timestamp,
                    samples = sampler.get().getThreadBlockageSamples(),
                )
            )
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
    private fun findLeastValuableIntervalWithSamples(intervals: List<ThreadBlockageInterval>): ThreadBlockageInterval? {
        return intervals.filter { it.hasSamples() }.minByOrNull { it.duration() }
    }

    private fun reachedIntervalCaptureLimit(intervals: List<ThreadBlockageInterval>): Boolean {
        val count = intervals.filter { it.hasSamples() }.size
        return count > maxIntervalsPerSession
    }

    /**
     * Calculates the duration of the interval, returning -1 if this is unknown.
     */
    private fun ThreadBlockageInterval.duration(): Long {
        return when (val end = endTime ?: lastKnownTime) {
            null -> -1
            else -> end - startTime
        }
    }

    @CheckResult
    private fun ThreadBlockageInterval.clearSamples(): ThreadBlockageInterval = copy(samples = null, code = CODE_SAMPLES_CLEARED)

    private fun ThreadBlockageInterval.hasSamples(): Boolean = code != CODE_SAMPLES_CLEARED

    private companion object {

        /**
         * Hard limit for the maximum number of intervals an SDK wants to send in a payload.
         * Not all of these intervals will have stacktrace samples associated with them
         * (that is set via a configurable limit).
         */
        private const val MAX_INTERVAL_COUNT = 100
    }
}
