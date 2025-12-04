package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrInterval
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Checks whether the target thread is still responding by using the following strategy:
 *
 *  1. Creating a [android.os.Handler], on the target thread, and an executor on a monitor thread
 *  2. Using the 'monitoring' thread to message the target thread with a heartbeat
 *  3. Determining whether the target thread responds in time, and if not logging an ANR
 */
internal class EmbraceAnrService(
    args: InstrumentationArgs,
    private val blockedThreadDetector: BlockedThreadDetector,
    private val anrMonitorWorker: BackgroundWorker,
    private val state: ThreadMonitoringState,
    private val stacktraceSampler: AnrStacktraceSampler,
    private val random: Random = Random.Default,
) : AnrService {

    private val logger = args.logger
    private val clock = args.clock
    private val appStateTracker = args.appStateTracker
    private val telemetryDestination = args.destination
    private var delayedBackgroundCheckTask: ScheduledFuture<*>? = null

    init {
        if (appStateTracker.getAppState() == AppState.BACKGROUND) {
            scheduleDelayedBackgroundCheck()
        }
    }

    override fun startAnrCapture() {
        this.anrMonitorWorker.submit {
            blockedThreadDetector.startMonitoringThread()
        }
    }

    /**
     * Gets the intervals during which the application was not responding (ANR).
     *
     * All functions in this class MUST be called from the same thread as [BlockedThreadDetector].
     * This is part of the synchronization strategy that ensures ANR data is not corrupted.
     */
    internal fun getCapturedData(): List<AnrInterval> {
        return try {
            val callable = Callable {
                checkNotNull(stacktraceSampler.getAnrIntervals(state, clock)) {
                    "ANR samples to be cached is null"
                }
            }
            anrMonitorWorker.submit(callable).get(MAX_DATA_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (exc: Exception) {
            logger.trackInternalError(InternalErrorType.ANR_DATA_FETCH, exc)
            emptyList()
        }
    }

    override fun handleCrash(crashId: String) {
        this.anrMonitorWorker.submit {
            blockedThreadDetector.stopMonitoringThread()
        }
    }

    override fun cleanCollections() {
        stacktraceSampler.cleanCollections()
    }

    /**
     * When app goes to foreground, we need to monitor the target thread again to
     * capture ANRs.
     */
    override fun onForeground() {
        this.anrMonitorWorker.submit {
            // Cancel any pending delayed background check since we're now in foreground
            cancelDelayedBackgroundCheck()
            state.resetState()
            blockedThreadDetector.startMonitoringThread()
        }
    }

    /**
     * When app goes to background, we stop monitoring the target thread
     * because we don't need to capture ANRs on background and we don't
     * want to affect customer's app performance.
     */
    override fun onBackground() {
        this.anrMonitorWorker.submit {
            blockedThreadDetector.stopMonitoringThread()
        }
    }

    /**
     * Schedules a delayed check to stop ANR monitoring if the app is still in background.
     * This handles slow app startup scenarios where the app takes time to transition to foreground.
     */
    private fun scheduleDelayedBackgroundCheck() {
        delayedBackgroundCheckTask = anrMonitorWorker.schedule<Unit>(::stopMonitoringIfStillInBackground, 10, TimeUnit.SECONDS)
    }

    /**
     * Cancels the delayed background check task if it exists.
     */
    private fun cancelDelayedBackgroundCheck() {
        delayedBackgroundCheckTask?.cancel(false)
        delayedBackgroundCheckTask = null
    }

    /**
     * Stops ANR monitoring if the app is currently in background state.
     * Called after a 10-second delay to handle slow startup scenarios.
     */
    private fun stopMonitoringIfStillInBackground() {
        if (appStateTracker.getAppState() == AppState.BACKGROUND) {
            blockedThreadDetector.stopMonitoringThread()
        }
        delayedBackgroundCheckTask = null
    }

    override fun snapshotSpans(): List<Span> = EmbTrace.trace("anr-snapshot") {
        getCapturedData().map { interval ->
            mapIntervalToSpan(interval, clock, random)
        }
    }

    override fun record() = EmbTrace.trace("anr-record") {
        getCapturedData().forEach { interval ->
            val attributes = mapIntervalToSpanAttributes(interval).toEmbracePayload()
            val events = interval.anrSampleList?.samples?.map {
                mapSampleToSpanEvent(it).toArchSpanEvent()
            } ?: emptyList()
            telemetryDestination.recordCompletedSpan(
                name = "thread-blockage",
                startTimeMs = interval.startTime,
                endTimeMs = interval.endTime ?: clock.now(),
                type = EmbType.Performance.ThreadBlockage,
                attributes = attributes,
                events = events,
            )
        }
    }

    private companion object {

        /**
         * The maximum number of milliseconds we should wait to retrieve ANR intervals to the
         * session payload. The vast majority of times this wait time should effectively be 0ms -
         * a limit is included to avoid blocking the main thread/sampling.
         */
        private const val MAX_DATA_WAIT_MS = 1000L
    }
}
