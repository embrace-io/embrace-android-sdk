package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Checks whether the target thread is still responding by using the following strategy:
 *
 *  1. Creating a [android.os.Handler], on the target thread, and an executor on a monitor thread
 *  2. Using the 'monitoring' thread to message the target thread with a heartbeat
 *  3. Determining whether the target thread responds in time and taking stacktrace samples if not
 */
internal class AnrServiceImpl(
    args: InstrumentationArgs,
    private val blockedThreadDetector: BlockedThreadDetector,
    private val watchdogWorker: BackgroundWorker,
    private val stacktraceSampler: AnrStacktraceSampler,
    private val random: Random = Random.Default,
) : AnrService {

    private val clock = args.clock
    private val appStateTracker = args.appStateTracker
    private val telemetryDestination = args.destination
    private var delayedBackgroundCheckTask: ScheduledFuture<*>? = null

    init {
        if (appStateTracker.getAppState() == AppState.BACKGROUND) {
            scheduleDelayedBackgroundCheck()
        }
    }

    override fun startCapture() {
        blockedThreadDetector.start()
    }

    override fun simulateTargetThreadResponse() {
        blockedThreadDetector.onTargetThreadProcessedMessage(clock.now())
    }

    override fun handleCrash(crashId: String) {
        blockedThreadDetector.stop()
    }

    override fun cleanCollections() {
        stacktraceSampler.cleanCollections()
    }

    /**
     * When app goes to foreground, we need to monitor the target thread again to
     * capture stacktrace samples.
     */
    override fun onForeground() {
        // Cancel any pending delayed background check since we're now in foreground
        cancelDelayedBackgroundCheck()
        blockedThreadDetector.start()
    }

    /**
     * When app goes to background, we stop monitoring the target thread
     * because we don't need to sample stacktraces on background and we don't
     * want to affect customer's app performance.
     */
    override fun onBackground() {
        blockedThreadDetector.stop()
    }

    override fun snapshotSpans(): List<Span> = EmbTrace.trace("anr-snapshot") {
        stacktraceSampler.getAnrIntervals().map { interval ->
            mapIntervalToSpan(interval, clock, random)
        }
    }

    override fun record() = EmbTrace.trace("anr-record") {
        stacktraceSampler.getAnrIntervals().forEach { interval ->
            val attributes = mapIntervalToSpanAttributes(interval).toEmbracePayload()
            val events = interval.samples?.map {
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

    /**
     * Schedules a delayed check to stop stacktrace sampling if the app is still in background.
     * This handles slow app startup scenarios where the app takes time to transition to foreground.
     */
    private fun scheduleDelayedBackgroundCheck() {
        delayedBackgroundCheckTask = watchdogWorker.schedule<Unit>(::stopMonitoringIfStillInBackground, 10, TimeUnit.SECONDS)
    }

    /**
     * Cancels the delayed background check task if it exists.
     */
    private fun cancelDelayedBackgroundCheck() {
        delayedBackgroundCheckTask?.cancel(false)
        delayedBackgroundCheckTask = null
    }

    /**
     * Stops stacktrace sampling monitoring if the app is currently in background state.
     * Called after a 10-second delay to handle slow startup scenarios.
     */
    private fun stopMonitoringIfStillInBackground() {
        if (appStateTracker.getAppState() == AppState.BACKGROUND) {
            blockedThreadDetector.stop()
        }
        delayedBackgroundCheckTask = null
    }
}
