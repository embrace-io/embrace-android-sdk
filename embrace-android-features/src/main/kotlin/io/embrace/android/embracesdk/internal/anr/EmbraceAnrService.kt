package io.embrace.android.embracesdk.internal.anr

import android.os.Looper
import io.embrace.android.embracesdk.internal.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.internal.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.AnrInterval
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

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
    logger: EmbLogger,
    livenessCheckScheduler: LivenessCheckScheduler,
    private val anrMonitorWorker: ScheduledWorker,
    state: ThreadMonitoringState,
    val clock: Clock
) : AnrService, MemoryCleanerListener, ProcessStateListener, BlockedThreadListener {

    private val state: ThreadMonitoringState
    private val targetThread: Thread
    val stacktraceSampler: AnrStacktraceSampler
    private val logger: EmbLogger
    private val targetThreadHeartbeatScheduler: LivenessCheckScheduler

    val listeners: CopyOnWriteArrayList<BlockedThreadListener> = CopyOnWriteArrayList<BlockedThreadListener>()

    init {
        targetThread = looper.thread
        this.logger = logger
        this.state = state
        targetThreadHeartbeatScheduler = livenessCheckScheduler
        stacktraceSampler = AnrStacktraceSampler(
            configService,
            clock,
            targetThread,
            anrMonitorWorker
        )

        // add listeners
        listeners.add(stacktraceSampler)
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
        targetThreadHeartbeatScheduler.configService = configService
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
            logger.logWarning("Failed to getAnrIntervals()", exc)
            logger.trackInternalError(InternalErrorType.ANR_DATA_FETCH, exc)
            emptyList()
        }
    }

    override fun handleCrash(crashId: String) {
        this.anrMonitorWorker.submit {
            targetThreadHeartbeatScheduler.stopMonitoringThread()
        }
    }

    override fun close() {
    }

    override fun cleanCollections() {
        stacktraceSampler.cleanCollections()
    }

    override fun onThreadBlocked(thread: Thread, timestamp: Long) {
        for (listener in listeners) {
            listener.onThreadBlocked(thread, timestamp)
        }
    }

    override fun onThreadBlockedInterval(thread: Thread, timestamp: Long) {
        processAnrTick(timestamp)
    }

    override fun onThreadUnblocked(thread: Thread, timestamp: Long) {
        for (listener in listeners) {
            listener.onThreadUnblocked(thread, timestamp)
        }
    }

    fun processAnrTick(timestamp: Long) {
        // Check if ANR capture is enabled
        if (!configService.anrBehavior.isAnrCaptureEnabled()) {
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

    private companion object {

        /**
         * The maximum number of milliseconds we should wait to retrieve ANR intervals to the
         * session payload. The vast majority of times this wait time should effectively be 0ms -
         * a limit is included to avoid blocking the main thread/sampling.
         */
        private const val MAX_DATA_WAIT_MS = 1000L
    }
}
