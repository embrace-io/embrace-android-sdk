package io.embrace.android.embracesdk.anr

import android.os.Looper
import androidx.annotation.VisibleForTesting
import io.embrace.android.embracesdk.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.anr.detection.UnbalancedCallDetector
import io.embrace.android.embracesdk.anr.sigquit.SigquitDetectionService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.enforceThread
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.AnrInterval
import io.embrace.android.embracesdk.session.MemoryCleanerListener
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Checks whether the target thread is still responding by using the following strategy:
 *
 *  1. Creating a [android.os.Handler], on the target thread, and an executor on a monitor thread
 *  1. Using the 'monitoring' thread to message the target thread with a heartbeat
 *  1. Determining whether the target thread responds in time, and if not logging an ANR
 */
internal class EmbraceAnrService(
    var configService: ConfigService,
    looper: Looper,
    logger: InternalEmbraceLogger,
    sigquitDetectionService: SigquitDetectionService,
    livenessCheckScheduler: LivenessCheckScheduler,
    private val anrMonitorWorker: ScheduledWorker,
    state: ThreadMonitoringState,
    @field:VisibleForTesting val clock: Clock,
    private val anrMonitorThread: AtomicReference<Thread>
) : AnrService, MemoryCleanerListener, ProcessStateListener, BlockedThreadListener {

    private val state: ThreadMonitoringState
    private val targetThread: Thread
    val stacktraceSampler: AnrStacktraceSampler
    private val logger: InternalEmbraceLogger
    private val sigquitDetectionService: SigquitDetectionService
    private val targetThreadHeartbeatScheduler: LivenessCheckScheduler

    val listeners = CopyOnWriteArrayList<BlockedThreadListener>()

    init {
        targetThread = looper.thread
        this.logger = logger
        this.sigquitDetectionService = sigquitDetectionService
        this.state = state
        targetThreadHeartbeatScheduler = livenessCheckScheduler
        stacktraceSampler = AnrStacktraceSampler(configService, clock, targetThread, anrMonitorThread, anrMonitorWorker)

        // add listeners
        listeners.add(stacktraceSampler)
        listeners.add(UnbalancedCallDetector(logger))
        livenessCheckScheduler.listener = this
    }

    private fun startAnrCapture() {
        this.anrMonitorWorker.submit {
            targetThreadHeartbeatScheduler.startMonitoringThread()
        }
    }

    override fun finishInitialization(
        configService: ConfigService
    ) {
        this.configService = configService
        stacktraceSampler.setConfigService(configService)
        sigquitDetectionService.configService = configService
        targetThreadHeartbeatScheduler.configService = configService
        logger.logDeveloper("EmbraceAnrService", "Finish initialization")
        sigquitDetectionService.initializeGoogleAnrTracking()
        startAnrCapture()
    }

    override fun addBlockedThreadListener(listener: BlockedThreadListener) {
        listeners.add(listener)
    }

    /**
     * Gets the intervals during which the application was not responding (ANR).
     *
     * All functions in this class MUST be called from the same thread as [BlockedThreadDetector].
     * This is part of the synchronization strategy that ensures ANR data is not corrupted.
     */
    override fun getCapturedData(): List<AnrInterval> {
        return try {
            val callable = Callable {
                checkNotNull(stacktraceSampler.getAnrIntervals(state, clock)) {
                    "ANR samples to be cached is null"
                }
            }
            anrMonitorWorker.submit(callable).get(MAX_DATA_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (exc: Exception) {
            logger.logError("Failed to getAnrIntervals()", exc, true)
            emptyList()
        }
    }

    override fun forceAnrTrackingStopOnCrash() {
        this.anrMonitorWorker.submit {
            targetThreadHeartbeatScheduler.stopMonitoringThread()
        }
    }

    override fun close() {
    }

    override fun cleanCollections() {
        stacktraceSampler.cleanCollections()
        sigquitDetectionService.cleanCollections()
    }

    override fun onThreadBlocked(thread: Thread, timestamp: Long) {
        enforceThread(anrMonitorThread)
        for (listener in listeners) {
            listener.onThreadBlocked(thread, timestamp)
        }
    }

    override fun onThreadBlockedInterval(thread: Thread, timestamp: Long) {
        enforceThread(anrMonitorThread)
        processAnrTick(timestamp)
    }

    override fun onThreadUnblocked(thread: Thread, timestamp: Long) {
        enforceThread(anrMonitorThread)
        for (listener in listeners) {
            listener.onThreadUnblocked(thread, timestamp)
        }
    }

    internal fun processAnrTick(timestamp: Long) {
        // Check if ANR capture is enabled
        if (!configService.anrBehavior.isAnrCaptureEnabled()) {
            logger.logDeveloper("EmbraceAnrService", "ANR capture is disabled, ignoring ANR tick")
            return
        }

        // Invoke callbacks
        for (listener in listeners) {
            listener.onThreadBlockedInterval(targetThread, timestamp)
        }
    }

    /**
     * When app goes to foreground, we need to monitor the target thread again to
     * capture ANRs.
     */
    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        this.anrMonitorWorker.submit {
            enforceThread(anrMonitorThread)
            state.resetState()
            targetThreadHeartbeatScheduler.startMonitoringThread()
        }
    }

    /**
     * When app goes to background, we stop monitoring the target thread
     * because we don't need to capture ANRs on background and we don't
     * want to affect customer's app performance.
     */
    override fun onBackground(timestamp: Long) {
        this.anrMonitorWorker.submit {
            targetThreadHeartbeatScheduler.stopMonitoringThread()
        }
    }

    companion object {

        /**
         * The maximum number of milliseconds we should wait to retrieve ANR intervals to the
         * session payload. The vast majority of times this wait time should effectively be 0ms -
         * a limit is included to avoid blocking the main thread/sampling.
         */
        private const val MAX_DATA_WAIT_MS = 1000L
    }
}
