package io.embrace.android.embracesdk.internal.capture.startup

import android.os.Build.VERSION_CODES
import android.os.Process
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.spans.PersistableEmbraceSpan
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records startup traces based on the data that it is provided. It adjusts what it logs based on what data has been provided and
 * and which Android version the app is running.
 *
 * For the start of cold launch traces, the start time is determined by the following:
 *
 * - Android 13 onwards, it is determined by when the app process is specialized from the zygote process, which could be some time after the
 *   the zygote process is created, depending on, perhaps among other things, the manufacturer of the device. If this value is used,
 *   it value will be captured in an attribute on the root span of the trace.
 *
 * - Android 7.0 to 12 (inclusive), it is determined by when the app process is created. Some OEMs on some versions of Android are known to
 *   pre-created a bunch of zygotes in order to speed up startup time, to mixed success. The fact that Pixel devices don't do this should
 *   tell you how effectively that strategy overall is.
 *
 * - Older Android version that are supported, if provided, the application object creation time. Otherwise, we fall back to
 *   SDK startup time (for now)
 *
 * For the start of warm launch traces, we use the creation time for the first activity that we want to consider for startup
 *
 */
internal class AppStartupTraceEmitter(
    private val clock: Clock,
    private val startupServiceProvider: Provider<StartupService?>,
    private val spanService: SpanService,
    private val backgroundWorker: BackgroundWorker,
    private val versionChecker: VersionChecker,
    private val logger: EmbLogger
) : AppStartupDataCollector {
    private val processCreateRequestedMs: Long?
    private val processCreatedMs: Long?
    private val additionalTrackedIntervals = ConcurrentLinkedQueue<TrackedInterval>()

    init {
        val timestampAtDeviceStart = nowMs() - clock.nanoTime().nanosToMillis()
        processCreateRequestedMs = if (versionChecker.isAtLeast(VERSION_CODES.TIRAMISU)) {
            timestampAtDeviceStart + Process.getStartRequestedElapsedRealtime()
        } else {
            null
        }
        processCreatedMs = if (versionChecker.isAtLeast(VERSION_CODES.N)) {
            timestampAtDeviceStart + Process.getStartElapsedRealtime()
        } else {
            null
        }
    }

    @Volatile
    private var applicationInitStartMs: Long? = null

    @Volatile
    private var applicationInitEndMs: Long? = null

    @Volatile
    private var startupActivityName: String? = null

    @Volatile
    private var firstActivityInitStartMs: Long? = null

    @Volatile
    private var startupActivityPreCreatedMs: Long? = null

    @Volatile
    private var startupActivityInitStartMs: Long? = null

    @Volatile
    private var startupActivityPostCreatedMs: Long? = null

    @Volatile
    private var startupActivityInitEndMs: Long? = null

    @Volatile
    private var startupActivityResumedMs: Long? = null

    @Volatile
    private var firstFrameRenderedMs: Long? = null

    @Volatile
    private var sdkInitThreadName: String? = null

    @Volatile
    private var sdkInitEndedInForeground: Boolean? = null

    private val startupRecorded = AtomicBoolean(false)
    private val endWithFrameDraw: Boolean = versionChecker.isAtLeast(VERSION_CODES.Q)

    override fun applicationInitStart(timestampMs: Long?) {
        applicationInitStartMs = timestampMs ?: nowMs()
    }

    override fun applicationInitEnd(timestampMs: Long?) {
        applicationInitEndMs = timestampMs ?: nowMs()
    }

    override fun startupActivityPreCreated(timestampMs: Long?) {
        startupActivityPreCreatedMs = timestampMs ?: nowMs()
    }

    override fun startupActivityInitStart(timestampMs: Long?) {
        startupActivityInitStartMs = (timestampMs ?: nowMs()).apply {
            if (firstActivityInitStartMs == null) {
                firstActivityInitStartMs = this
            }
        }
    }

    override fun startupActivityPostCreated(timestampMs: Long?) {
        startupActivityPostCreatedMs = timestampMs ?: nowMs()
    }

    override fun startupActivityInitEnd(timestampMs: Long?) {
        startupActivityInitEndMs = timestampMs ?: nowMs()
    }

    override fun startupActivityResumed(activityName: String, timestampMs: Long?) {
        startupActivityName = activityName
        startupActivityResumedMs = timestampMs ?: nowMs()
        if (!endWithFrameDraw) {
            dataCollectionComplete()
        }
    }

    override fun firstFrameRendered(activityName: String, timestampMs: Long?) {
        startupActivityName = activityName
        firstFrameRenderedMs = timestampMs ?: nowMs()
        if (endWithFrameDraw) {
            dataCollectionComplete()
        }
    }

    override fun addTrackedInterval(name: String, startTimeMs: Long, endTimeMs: Long) {
        additionalTrackedIntervals.add(
            TrackedInterval(name = name, startTimeMs = startTimeMs, endTimeMs = endTimeMs)
        )
    }

    /**
     * Called when app startup is considered complete, i.e. the data can be used and any additional updates can be ignored
     */
    private fun dataCollectionComplete() {
        if (!startupRecorded.get()) {
            synchronized(startupRecorded) {
                if (!startupRecorded.get()) {
                    backgroundWorker.submit {
                        recordStartup()
                        if (!startupRecorded.get()) {
                            logger.logWarning("App startup trace recording attempted but did not succeed")
                        }
                    }
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "ComplexMethod")
    private fun recordStartup() {
        val startupService = startupServiceProvider() ?: return
        val sdkInitStartMs = startupService.getSdkInitStartMs()
        val sdkInitEndMs = startupService.getSdkInitEndMs()
        val sdkStartupDuration = duration(sdkInitStartMs, sdkInitEndMs)
        val processStartTimeMs: Long? =
            if (versionChecker.isAtLeast(VERSION_CODES.N)) {
                processCreatedMs
            } else {
                applicationInitStartMs ?: sdkInitStartMs
            }

        val traceEndTimeMs: Long? =
            if (versionChecker.isAtLeast(VERSION_CODES.Q)) {
                firstFrameRenderedMs
            } else {
                startupActivityResumedMs
            }

        sdkInitEndedInForeground = startupService.endedInForeground()
        sdkInitThreadName = startupService.getInitThreadName()

        if (processStartTimeMs != null && traceEndTimeMs != null && sdkInitEndMs != null) {
            val gap = applicationActivityCreationGap(sdkInitEndMs)
            if (gap != null) {
                val startupTrace: EmbraceSpan? = if (!spanService.initialized()) {
                    logger.logWarning("Startup trace not recorded because the spans service is not initialized")
                    null
                } else if (gap <= SDK_AND_ACTIVITY_INIT_GAP) {
                    recordColdTtid(
                        traceStartTimeMs = processStartTimeMs,
                        applicationInitEndMs = applicationInitEndMs,
                        sdkInitStartMs = sdkInitStartMs,
                        sdkInitEndMs = sdkInitEndMs,
                        firstActivityInitMs = firstActivityInitStartMs,
                        activityInitStartMs = startupActivityInitStartMs,
                        activityInitEndMs = startupActivityInitEndMs,
                        traceEndTimeMs = traceEndTimeMs
                    )
                } else {
                    val warmStartTimeMs = firstActivityInitStartMs ?: startupActivityInitStartMs
                    warmStartTimeMs?.let { startTime ->
                        recordWarmTtid(
                            traceStartTimeMs = startTime,
                            activityInitStartMs = startupActivityInitStartMs,
                            activityInitEndMs = startupActivityInitEndMs,
                            traceEndTimeMs = traceEndTimeMs,
                            processToActivityCreateGap = gap,
                            sdkStartupDuration = sdkStartupDuration,
                        )
                    }
                }

                if (startupTrace != null) {
                    recordAdditionalIntervals(startupTrace)
                }
            }
        }
    }

    private fun recordAdditionalIntervals(startupTrace: EmbraceSpan) {
        do {
            additionalTrackedIntervals.poll()?.let { trackedInterval ->
                spanService.recordCompletedSpan(
                    name = trackedInterval.name,
                    parent = startupTrace,
                    startTimeMs = trackedInterval.startTimeMs,
                    endTimeMs = trackedInterval.endTimeMs,
                )
            }
        } while (additionalTrackedIntervals.isNotEmpty())
    }

    @Suppress("CyclomaticComplexMethod", "ComplexMethod")
    private fun recordColdTtid(
        traceStartTimeMs: Long,
        applicationInitEndMs: Long?,
        sdkInitStartMs: Long?,
        sdkInitEndMs: Long?,
        firstActivityInitMs: Long?,
        activityInitStartMs: Long?,
        activityInitEndMs: Long?,
        traceEndTimeMs: Long,
    ): EmbraceSpan? {
        return if (!startupRecorded.get()) {
            spanService.startSpan(
                name = "cold-time-to-initial-display",
                startTimeMs = traceStartTimeMs,
            )?.apply {
                addTraceMetadata()

                if (stop(endTimeMs = traceEndTimeMs)) {
                    startupRecorded.set(true)
                }

                if (applicationInitEndMs != null) {
                    spanService.recordCompletedSpan(
                        name = "process-init",
                        parent = this,
                        startTimeMs = traceStartTimeMs,
                        endTimeMs = applicationInitEndMs,
                    )
                }
                if (sdkInitStartMs != null && sdkInitEndMs != null) {
                    spanService.recordCompletedSpan(
                        name = "embrace-init",
                        parent = this,
                        startTimeMs = sdkInitStartMs,
                        endTimeMs = sdkInitEndMs,
                    )
                }
                val lastEventBeforeActivityInit = applicationInitEndMs ?: sdkInitEndMs
                val firstActivityInit = firstActivityInitMs ?: activityInitStartMs
                if (lastEventBeforeActivityInit != null && firstActivityInit != null) {
                    spanService.recordCompletedSpan(
                        name = "activity-init-gap",
                        parent = this,
                        startTimeMs = lastEventBeforeActivityInit,
                        endTimeMs = firstActivityInit,
                    )
                }
                if (activityInitStartMs != null && activityInitEndMs != null) {
                    spanService.recordCompletedSpan(
                        name = "activity-create",
                        parent = this,
                        startTimeMs = activityInitStartMs,
                        endTimeMs = activityInitEndMs,
                    )
                }
                if (activityInitEndMs != null) {
                    val uiLoadSpanName = if (endWithFrameDraw) {
                        "first-frame-render"
                    } else {
                        "activity-resume"
                    }
                    spanService.recordCompletedSpan(
                        name = uiLoadSpanName,
                        parent = this,
                        startTimeMs = activityInitEndMs,
                        endTimeMs = traceEndTimeMs,
                    )
                }
            }
        } else {
            null
        }
    }

    private fun recordWarmTtid(
        traceStartTimeMs: Long,
        activityInitStartMs: Long?,
        activityInitEndMs: Long?,
        traceEndTimeMs: Long,
        processToActivityCreateGap: Long?,
        sdkStartupDuration: Long?,
    ): EmbraceSpan? {
        return if (!startupRecorded.get()) {
            spanService.startSpan(
                name = "warm-time-to-initial-display",
                startTimeMs = traceStartTimeMs,
            )?.apply {
                processToActivityCreateGap?.let { gap ->
                    addAttribute("activity-init-gap-ms", gap.toString())
                }
                sdkStartupDuration?.let { duration ->
                    addAttribute("embrace-init-duration-ms", duration.toString())
                }

                addTraceMetadata()

                if (stop(endTimeMs = traceEndTimeMs)) {
                    startupRecorded.set(true)
                }
                if (activityInitStartMs != null && activityInitEndMs != null) {
                    spanService.recordCompletedSpan(
                        name = "activity-create",
                        parent = this,
                        startTimeMs = activityInitStartMs,
                        endTimeMs = activityInitEndMs,
                    )
                    val uiLoadSpanName = if (endWithFrameDraw) {
                        "first-frame-render"
                    } else {
                        "activity-resume"
                    }
                    spanService.recordCompletedSpan(
                        name = uiLoadSpanName,
                        parent = this,
                        startTimeMs = activityInitEndMs,
                        endTimeMs = traceEndTimeMs,
                    )
                }
            }
        } else {
            null
        }
    }

    private fun processCreateDelay(): Long? = duration(processCreateRequestedMs, processCreatedMs)

    private fun applicationActivityCreationGap(sdkInitEndMs: Long): Long? =
        duration(applicationInitEndMs ?: sdkInitEndMs, firstActivityInitStartMs)

    private fun nowMs(): Long = clock.now().nanosToMillis()

    private fun PersistableEmbraceSpan.addTraceMetadata() {
        processCreateDelay()?.let { delay ->
            addAttribute("process-create-delay-ms", delay.toString())
        }

        startupActivityName?.let { name ->
            addAttribute("startup-activity-name", name)
        }

        startupActivityPreCreatedMs?.let { timeMs ->
            addAttribute("startup-activity-pre-created-ms", timeMs.toString())
        }

        startupActivityPostCreatedMs?.let { timeMs ->
            addAttribute("startup-activity-post-created-ms", timeMs.toString())
        }

        sdkInitEndedInForeground?.let { inForeground ->
            addAttribute("embrace-init-in-foreground", inForeground.toString())
        }

        firstActivityInitStartMs?.let { timeMs ->
            addAttribute("first-activity-init-ms", timeMs.toString())
        }

        sdkInitThreadName?.let { threadName ->
            addAttribute("embrace-init-thread-name", threadName)
        }
    }

    private data class TrackedInterval(
        val name: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
    )

    companion object {
        /**
         * The gap between the end of the Embrace SDK initialization to when the first activity loaded after startup happens.
         * If this gap is greater than 2 seconds, it is assumed that the app process was not created because the user tapped on the app,
         * so we track this app launch as a warm start.
         */
        const val SDK_AND_ACTIVITY_INIT_GAP = 2000L

        fun duration(start: Long?, end: Long?): Long? = if (start != null && end != null) {
            end - start
        } else {
            null
        }
    }
}
