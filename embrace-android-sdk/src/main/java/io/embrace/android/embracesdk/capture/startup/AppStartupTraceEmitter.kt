package io.embrace.android.embracesdk.capture.startup

import android.os.Build.VERSION_CODES
import android.os.Process
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records startup traces based on the data that it is provided. It adjusts what it logs based on what data has been provided and
 * and which Android version the app is running.
 */
internal class AppStartupTraceEmitter(
    private val clock: Clock,
    private val startupServiceProvider: Provider<StartupService?>,
    private val spanService: SpanService,
    private val backgroundWorker: BackgroundWorker,
    private val versionChecker: VersionChecker,
    private val logger: InternalEmbraceLogger
) {
    private val processCreateRequestedMs: Long?
    private val processCreatedMs: Long?

    init {
        val timestampAtDeviceStart = nowMs() - clock.nanoTime().nanosToMillis()
        processCreateRequestedMs = if (versionChecker.isAtLeast(VERSION_CODES.N)) {
            timestampAtDeviceStart + Process.getStartElapsedRealtime()
        } else {
            null
        }
        processCreatedMs = if (versionChecker.isAtLeast(VERSION_CODES.TIRAMISU)) {
            timestampAtDeviceStart + Process.getStartRequestedElapsedRealtime()
        } else {
            null
        }
    }

    @Volatile
    private var applicationInitStartMs: Long? = null

    @Volatile
    private var applicationInitEndMs: Long? = null

    @Volatile
    private var startupActivityInitStartMs: Long? = null

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

    fun startupActivityInitStart(timestampMs: Long? = null) {
        if (startupActivityInitStartMs == null) {
            startupActivityInitStartMs = timestampMs ?: nowMs()
        }
    }

    fun startupActivityInitEnd(timestampMs: Long? = null) {
        startupActivityInitEndMs = timestampMs ?: nowMs()
    }

    fun startupActivityResumed(timestampMs: Long? = null) {
        startupActivityResumedMs = timestampMs ?: nowMs()
        if (!endWithFrameDraw) {
            dataCollectionComplete()
        }
    }

    fun firstFrameRendered(timestampMs: Long? = null) {
        firstFrameRenderedMs = timestampMs ?: nowMs()
        if (endWithFrameDraw) {
            dataCollectionComplete()
        }
    }

    private fun dataCollectionComplete() {
        if (!startupRecorded.get()) {
            synchronized(startupRecorded) {
                if (!startupRecorded.get()) {
                    recordStartup()
                    if (!startupRecorded.get()) {
                        logger.logWarning("App startup trace recording attempted but did not succeed")
                    }
                }
            }
        }
    }

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
                if (!spanService.initialized()) {
                    logger.logWarning("Startup trace not recorded because the spans service is not initialized")
                } else if (gap <= SDK_AND_ACTIVITY_INIT_GAP) {
                    recordColdTtid(
                        traceStartTimeMs = processStartTimeMs,
                        applicationInitEndMs = applicationInitEndMs,
                        sdkInitStartMs = sdkInitStartMs,
                        sdkInitEndMs = sdkInitEndMs,
                        activityInitStartMs = startupActivityInitStartMs,
                        activityInitEndMs = startupActivityInitEndMs,
                        traceEndTimeMs = traceEndTimeMs,
                        processCreateDelay = processCreateDelay(),
                    )
                } else {
                    startupActivityInitStartMs?.let { startTime ->
                        recordWarmTtid(
                            traceStartTimeMs = startTime,
                            activityInitEndMs = startupActivityInitEndMs,
                            traceEndTimeMs = traceEndTimeMs,
                            processCreateDelay = processCreateDelay(),
                            processToActivityCreateGap = gap,
                            sdkStartupDuration = sdkStartupDuration
                        )
                    }
                }
            }
        }
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
        processCreateDelay: Long?,
    ) {
        if (!startupRecorded.get()) {
            spanService.startSpan(
                name = "cold-time-to-initial-display",
                startTimeMs = traceStartTimeMs,
            )?.run {
                processCreateDelay?.let { delay ->
                    addAttribute("process-create-delay-ms".toEmbraceAttributeName(), delay.toString())
                }
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
                if (lastEventBeforeActivityInit != null && activityInitStartMs != null) {
                    spanService.recordCompletedSpan(
                        name = "activity-init-gap",
                        parent = this,
                        startTimeMs = lastEventBeforeActivityInit,
                        endTimeMs = activityInitStartMs,
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
        }
    }

    private fun recordWarmTtid(
        traceStartTimeMs: Long,
        activityInitEndMs: Long?,
        traceEndTimeMs: Long,
        processCreateDelay: Long?,
        processToActivityCreateGap: Long?,
        sdkStartupDuration: Long?,
    ) {
        if (!startupRecorded.get()) {
            spanService.startSpan(
                name = "warm-time-to-initial-display",
                startTimeMs = traceStartTimeMs,
            )?.run {
                processCreateDelay?.let { delay ->
                    addAttribute("process-create-delay-ms".toEmbraceAttributeName(), delay.toString())
                }
                processToActivityCreateGap?.let { gap ->
                    addAttribute("activity-init-gap-ms".toEmbraceAttributeName(), gap.toString())
                }
                sdkStartupDuration?.let { duration ->
                    addAttribute("embrace-init-duration-ms".toEmbraceAttributeName(), duration.toString())
                }
                if (stop(endTimeMs = traceEndTimeMs)) {
                    startupRecorded.set(true)
                }
                if (activityInitEndMs != null) {
                    spanService.recordCompletedSpan(
                        name = "activity-create",
                        parent = this,
                        startTimeMs = traceStartTimeMs,
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
        }
    }

    private fun processCreateDelay(): Long? = duration(processCreateRequestedMs, processCreatedMs)

    private fun applicationActivityCreationGap(): Long? = duration(applicationInitEndMs, startupActivityInitStartMs)

    private fun nowMs(): Long = clock.now().nanosToMillis()

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
