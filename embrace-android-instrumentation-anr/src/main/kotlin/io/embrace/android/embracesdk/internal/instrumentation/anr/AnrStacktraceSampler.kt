package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.arch.stacktrace.getThreadInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageEvent
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageListener
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrInterval
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrSample
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrSampleList
import io.embrace.android.embracesdk.internal.payload.ThreadInfo
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.CopyOnWriteArrayList

/**
 * This class is responsible for tracking the state of JVM stacktraces sampled during an ANR.
 */
internal class AnrStacktraceSampler(
    private val clock: Clock,
    private val targetThread: Thread,
    private val anrMonitorWorker: BackgroundWorker,
    private val maxIntervalsPerSession: Int,
    private val maxStacktracesPerInterval: Int,
    private val stacktraceFrameLimit: Int,
) : ThreadBlockageListener, MemoryCleanerListener {

    val anrIntervals: CopyOnWriteArrayList<AnrInterval> = CopyOnWriteArrayList<AnrInterval>()
    private val samples = mutableListOf<AnrSample>()
    private val currentStacktraceStates: MutableMap<Long, ThreadInfo> = HashMap()
    private var lastUnblockedMs: Long = 0

    fun size(): Int = samples.size

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

    private fun onThreadBlocked(timestamp: Long) {
        clearStacktraceCache()
        lastUnblockedMs = timestamp
    }

    private fun onThreadBlockedInterval(timestamp: Long) {
        val limit = maxStacktracesPerInterval
        val anrSample = if (size() >= limit) {
            AnrSample(timestamp, null, 0, AnrSample.CODE_SAMPLE_LIMIT_REACHED)
        } else {
            val start = clock.now()
            val threads = captureSample()
            val sampleOverheadMs = clock.now() - start
            AnrSample(timestamp, threads, sampleOverheadMs)
        }
        samples.add(anrSample)
    }

    private fun onThreadUnblocked(timestamp: Long) {
        // Finalize AnrInterval
        val responseMs = lastUnblockedMs
        val sanitizedSamples = samples.filter { it.timestamp in responseMs..timestamp }
        val anrInterval = AnrInterval(
            responseMs,
            null,
            timestamp,
            AnrInterval.Type.UI,
            AnrSampleList(sanitizedSamples)
        )

        synchronized(anrIntervals) {
            if (anrIntervals.size < MAX_ANR_INTERVAL_COUNT) {
                anrIntervals.add(anrInterval)

                while (reachedAnrStacktraceCaptureLimit()) {
                    findLeastValuableIntervalWithSamples()?.let { entry ->
                        val index = anrIntervals.indexOf(entry)
                        anrIntervals.remove(entry)
                        anrIntervals.add(index, entry.clearSamples())
                    }
                }
            }
        }

        // reset state
        samples.clear()
        lastUnblockedMs = timestamp
        clearStacktraceCache()
    }

    /**
     * Finds the 'least valuable' ANR interval. This is used when the maximum number of ANR
     * intervals with samples has been reached & the SDK needs to discard samples. We attempt
     * to pick the least valuable interval in this case.
     */
    fun findLeastValuableIntervalWithSamples(): AnrInterval? =
        findIntervalsWithSamples().minByOrNull(AnrInterval::duration)

    override fun cleanCollections() {
        anrMonitorWorker.submit {
            anrIntervals.removeAll { it.endTime != null }
        }
    }

    fun reachedAnrStacktraceCaptureLimit(): Boolean {
        val count = findIntervalsWithSamples().size
        return count > maxIntervalsPerSession
    }

    private fun findIntervalsWithSamples() = anrIntervals.filter(AnrInterval::hasSamples)

    /**
     * Retrieves ANR intervals that match the given start/time windows.
     */
    fun getAnrIntervals(
        state: ThreadMonitoringState,
        clock: Clock,
    ): List<AnrInterval> {
        synchronized(anrIntervals) {
            val results = anrIntervals.toMutableList()

            // add any in-progress ANRs
            if (state.anrInProgress) {
                val intervalEndTime = clock.now()
                val responseMs = state.lastTargetThreadResponseMs
                val anrInterval = AnrInterval(
                    responseMs,
                    intervalEndTime,
                    null,
                    AnrInterval.Type.UI,
                    AnrSampleList(samples.toList())
                )
                results.add(anrInterval)
            }
            return results.map(AnrInterval::deepCopy)
        }
    }

    /**
     * Clears the stacktrace cache for all threads.
     */
    private fun clearStacktraceCache(): Unit = currentStacktraceStates.clear()

    /**
     * Captures the thread traces required for the given sample.
     */
    private fun captureSample(): List<ThreadInfo> {
        val threadInfo = getMainThread()
        val sanitizedThreads = mutableListOf<ThreadInfo>()

        // Compares main thread with the last known thread state via hashcode. If hashcode changed
        // it should be added to the anrInfo list and also the currentAnrInfoState must be updated.
        val threadId = threadInfo.threadId
        val cache: ThreadInfo? = currentStacktraceStates[threadId]

        // only serialize if the previous stacktrace doesn't match.
        if (cache == null || threadInfo != cache) {
            sanitizedThreads.add(threadInfo)
            currentStacktraceStates[threadId] = threadInfo
        }
        return sanitizedThreads
    }

    /**
     * Filter the thread list based on allow/block list get by config.
     *
     * @return filtered threads
     */
    private fun getMainThread(): ThreadInfo = getThreadInfo(
        targetThread,
        targetThread.stackTrace,
        stacktraceFrameLimit
    )

    private companion object {

        /**
         * Hard limit for the maximum number of ANR intervals an SDK wants to send in a payload.
         * Not all of these intervals will have stacktrace samples associated with them
         * (that is set via a configurable limit).
         */
        private const val MAX_ANR_INTERVAL_COUNT = 100
    }
}
