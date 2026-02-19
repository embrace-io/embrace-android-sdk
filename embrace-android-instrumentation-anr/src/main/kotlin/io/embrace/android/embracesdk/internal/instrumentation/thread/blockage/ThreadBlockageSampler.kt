package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import androidx.annotation.CheckResult
import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.stacktrace.ThreadSample
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageInterval.Companion.CODE_SAMPLES_CLEARED
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageSample.Companion.CODE_DEFAULT
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageSample.Companion.CODE_SAMPLE_LIMIT_REACHED
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * This class is responsible for tracking the state of JVM stacktraces sampled during a thread blockage.
 */
class ThreadBlockageSampler(
    private val clock: Clock,
    private val targetThread: Thread,
    private val maxIntervalsPerSession: Int,
    private val maxSamplesPerInterval: Int,
    private val stacktraceFrameLimit: Int,
) : ThreadBlockageListener, SessionChangeListener {

    private val intervalSink = AtomicReference(CopyOnWriteArrayList<ThreadBlockageInterval>())
    private val currentBlockage = AtomicReference<CurrentBlockage?>(null)

    override fun onThreadBlockageEvent(
        event: ThreadBlockageEvent,
        timestamp: Long,
    ) {
        when (event) {
            ThreadBlockageEvent.BLOCKED -> {
                currentBlockage.set(
                    CurrentBlockage(
                        sampler = ThreadStacktraceSampler(
                            clock = clock,
                            targetThread = targetThread,
                            sampleLimit = maxSamplesPerInterval,
                            stacktraceFrameLimit = stacktraceFrameLimit
                        ),
                        startTime = timestamp
                    )
                )
            }
            ThreadBlockageEvent.BLOCKED_INTERVAL -> {
                currentBlockage.get()?.sampler?.captureSample()
            }
            ThreadBlockageEvent.UNBLOCKED -> {
                currentBlockage.get()?.apply {
                    val intervals = intervalSink.get()
                    if (intervals.size < MAX_INTERVAL_COUNT) {
                        intervals.add(
                            ThreadBlockageInterval(
                                startTime = startTime,
                                endTime = timestamp,
                                samples = sampler.getThreadBlockageSamples(),
                            )
                        )
                    }
                    currentBlockage.set(null)
                }
            }
        }
    }

    /**
     * Retrieves thread blockage intervals for the current session
     */
    fun getThreadBlockageIntervals(): List<ThreadBlockageInterval> {
        val blockage = currentBlockage.get()
        var results = intervalSink.get().toMutableList()

        if (blockage != null) {
            if (currentBlockage.get() === blockage) {
                results.add(
                    ThreadBlockageInterval(
                        startTime = blockage.startTime,
                        lastKnownTime = clock.now(),
                        samples = blockage.sampler.getThreadBlockageSamples(),
                    )
                )
            } else {
                results = intervalSink.get().toMutableList()
            }
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

    override fun onPostSessionChange() {
        intervalSink.set(CopyOnWriteArrayList(intervalSink.get().filter { it.endTime == null }))
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
    private fun ThreadBlockageInterval.clearSamples(): ThreadBlockageInterval = copy(
        samples = null,
        code = CODE_SAMPLES_CLEARED
    )

    private fun ThreadBlockageInterval.hasSamples(): Boolean = code != CODE_SAMPLES_CLEARED

    private class CurrentBlockage(
        val sampler: ThreadStacktraceSampler,
        val startTime: Long,
    )

    private companion object {

        /**
         * Hard limit for the maximum number of intervals an SDK wants to send in a payload.
         * Not all of these intervals will have stacktrace samples associated with them
         * (that is set via a configurable limit).
         */
        private const val MAX_INTERVAL_COUNT = 100
    }
}
