package io.embrace.android.embracesdk.capture.startup

import android.os.Build.VERSION_CODES
import android.os.Process
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
import io.embrace.android.embracesdk.worker.BackgroundWorker
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
) {
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

    private val startupRecorded = AtomicBoolean(false)
    private val endWithFrameDraw: Boolean = versionChecker.isAtLeast(VERSION_CODES.Q)

    fun applicationInitStart(timestampMs: Long? = null) {
        applicationInitStartMs = timestampMs ?: nowMs()
    }

    fun applicationInitEnd(timestampMs: Long? = null) {
        applicationInitEndMs = timestampMs ?: nowMs()
    }

    fun startupActivityPreCreated(timestampMs: Long? = null) {
        startupActivityPreCreatedMs = timestampMs ?: nowMs()
    }

    fun startupActivityInitStart(timestampMs: Long? = null) {
        startupActivityInitStartMs = timestampMs ?: nowMs()
    }

    fun startupActivityPostCreated(timestampMs: Long? = null) {
        startupActivityPostCreatedMs = timestampMs ?: nowMs()
    }

    fun startupActivityInitEnd(timestampMs: Long? = null) {
        startupActivityInitEndMs = timestampMs ?: nowMs()
    }

    fun startupActivityResumed(activityName: String, timestampMs: Long? = null) {
        startupActivityName = activityName
        startupActivityResumedMs = timestampMs ?: nowMs()
        if (!endWithFrameDraw) {
            dataCollectionComplete()
        }
    }

    fun firstFrameRendered(activityName: String, timestampMs: Long? = null) {
        startupActivityName = activityName
        firstFrameRenderedMs = timestampMs ?: nowMs()
        if (endWithFrameDraw) {
            dataCollectionComplete()
        }
    }

    fun addTrackedInterval(name: String, startTimeMs: Long, endTimeMs: Long) {
        additionalTrackedIntervals.add(
            TrackedInterval(name = name, startTimeMs = startTimeMs, endTimeMs = endTimeMs)
        )
    }

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

        if (processStartTimeMs != null && traceEndTimeMs != null) {
            val gap = applicationActivityCreationGap() ?: duration(sdkInitEndMs, startupActivityInitStartMs)
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
                        activityInitStartMs = startupActivityInitStartMs,
                        activityInitEndMs = startupActivityInitEndMs,
                        traceEndTimeMs = traceEndTimeMs,
                    )
                } else {
                    startupActivityInitStartMs?.let { startTime ->
                        recordWarmTtid(
                            traceStartTimeMs = startTime,
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
                    private = false
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
        activityInitStartMs: Long?,
        activityInitEndMs: Long?,
        traceEndTimeMs: Long,
    ): EmbraceSpan? {
        return if (!startupRecorded.get()) {
            spanService.startSpan(
                name = "cold-time-to-initial-display",
                startTimeMs = traceStartTimeMs,
                private = false
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
                        private = false,
                    )
                }
                if (sdkInitStartMs != null && sdkInitEndMs != null) {
                    spanService.recordCompletedSpan(
                        name = "embrace-init",
                        parent = this,
                        startTimeMs = sdkInitStartMs,
                        endTimeMs = sdkInitEndMs,
                        private = false,
                    )
                }
                val lastEventBeforeActivityInit = applicationInitEndMs ?: sdkInitEndMs
                if (lastEventBeforeActivityInit != null && activityInitStartMs != null) {
                    spanService.recordCompletedSpan(
                        name = "activity-init-gap",
                        parent = this,
                        startTimeMs = lastEventBeforeActivityInit,
                        endTimeMs = activityInitStartMs,
                        private = false,
                    )
                }
                if (activityInitStartMs != null && activityInitEndMs != null) {
                    spanService.recordCompletedSpan(
                        name = "activity-create",
                        parent = this,
                        startTimeMs = activityInitStartMs,
                        endTimeMs = activityInitEndMs,
                        private = false,
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
                        private = false,
                    )
                }
            }
        } else {
            null
        }
    }

    private fun recordWarmTtid(
        traceStartTimeMs: Long,
        activityInitEndMs: Long?,
        traceEndTimeMs: Long,
        processToActivityCreateGap: Long?,
        sdkStartupDuration: Long?,
    ): EmbraceSpan? {
        return if (!startupRecorded.get()) {
            spanService.startSpan(
                name = "warm-time-to-initial-display",
                startTimeMs = traceStartTimeMs,
                private = false,
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
                if (activityInitEndMs != null) {
                    spanService.recordCompletedSpan(
                        name = "activity-create",
                        parent = this,
                        startTimeMs = traceStartTimeMs,
                        endTimeMs = activityInitEndMs,
                        private = false,
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
                        private = false,
                    )
                }
            }
        } else {
            null
        }
    }

    private fun processCreateDelay(): Long? = duration(processCreateRequestedMs, processCreatedMs)

    private fun applicationActivityCreationGap(): Long? = duration(applicationInitEndMs, startupActivityInitStartMs)

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
    }

    private data class TrackedInterval(
        val name: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
    )

    companion object {
        /**
         * The gap between the end of the Embrace SDK initialization to when the first activity loaded after startup happens.
         * If this gap is greater than 1 minute, it's likely that the app process was not created because the user tapped on the app icon,
         * so we track this app launch as a warm start.
         */
        const val SDK_AND_ACTIVITY_INIT_GAP = 60000L

        fun duration(start: Long?, end: Long?): Long? = if (start != null && end != null) {
            end - start
        } else {
            null
        }
    }
}
