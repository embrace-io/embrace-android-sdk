package io.embrace.android.embracesdk.internal.anr

import io.embrace.android.embracesdk.internal.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.enforceThread
import io.embrace.android.embracesdk.internal.payload.AnrInterval
import io.embrace.android.embracesdk.internal.payload.AnrSample
import io.embrace.android.embracesdk.internal.payload.AnrSampleList
import io.embrace.android.embracesdk.internal.payload.extensions.clearSamples
import io.embrace.android.embracesdk.internal.payload.extensions.deepCopy
import io.embrace.android.embracesdk.internal.payload.extensions.duration
import io.embrace.android.embracesdk.internal.payload.extensions.hasSamples
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * This class is responsible for tracking the state of JVM stacktraces sampled during an ANR.
 */
internal class AnrStacktraceSampler(
    private var configService: ConfigService,
    private val clock: Clock,
    targetThread: Thread,
    private val anrMonitorThread: AtomicReference<Thread>,
    private val anrMonitorWorker: ScheduledWorker
) : BlockedThreadListener, MemoryCleanerListener {

    internal val anrIntervals = CopyOnWriteArrayList<AnrInterval>()
    private val samples = mutableListOf<AnrSample>()
    private var lastUnblockedMs: Long = 0
    private val threadInfoCollector = ThreadInfoCollector(targetThread)

    fun setConfigService(configService: ConfigService) {
        this.configService = configService
    }

    internal fun size() = samples.size

    override fun onThreadBlocked(thread: Thread, timestamp: Long) {
        threadInfoCollector.clearStacktraceCache()
        lastUnblockedMs = timestamp
    }

    override fun onThreadBlockedInterval(thread: Thread, timestamp: Long) {
        val limit = configService.anrBehavior.getMaxStacktracesPerInterval()
        val anrSample = if (size() >= limit) {
            AnrSample(timestamp, null, 0, AnrSample.CODE_SAMPLE_LIMIT_REACHED)
        } else {
            val start = clock.now()
            val threads = threadInfoCollector.captureSample(configService)
            val sampleOverheadMs = clock.now() - start
            AnrSample(timestamp, threads, sampleOverheadMs)
        }
        samples.add(anrSample)
    }

    override fun onThreadUnblocked(thread: Thread, timestamp: Long) {
        // Finalize AnrInterval
        val responseMs = lastUnblockedMs
        val anrInterval = AnrInterval(
            responseMs,
            null,
            timestamp,
            AnrInterval.Type.UI,
            AnrSampleList(samples.toList())
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
        threadInfoCollector.clearStacktraceCache()
    }

    /**
     * Finds the 'least valuable' ANR interval. This is used when the maximum number of ANR
     * intervals with samples has been reached & the SDK needs to discard samples. We attempt
     * to pick the least valuable interval in this case.
     */

    internal fun findLeastValuableIntervalWithSamples() =
        findIntervalsWithSamples().minByOrNull(AnrInterval::duration)

    override fun cleanCollections() {
        anrMonitorWorker.submit {
            enforceThread(anrMonitorThread)
            anrIntervals.removeAll { it.endTime != null }
        }
    }

    internal fun reachedAnrStacktraceCaptureLimit(): Boolean {
        val limit = configService.anrBehavior.getMaxAnrIntervalsPerSession()
        val count = findIntervalsWithSamples().size
        return count > limit
    }

    private fun findIntervalsWithSamples() = anrIntervals.filter(AnrInterval::hasSamples)

    /**
     * Retrieves ANR intervals that match the given start/time windows.
     */
    fun getAnrIntervals(
        state: ThreadMonitoringState,
        clock: Clock
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

    companion object {

        /**
         * Hard limit for the maximum number of ANR intervals an SDK wants to send in a payload.
         * Not all of these intervals will have stacktrace samples associated with them
         * (that is set via a configurable limit).
         */
        private const val MAX_ANR_INTERVAL_COUNT = 100
    }
}
