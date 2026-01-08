package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.os.Build.VERSION_CODES
import io.embrace.android.embracesdk.internal.arch.attrs.embStartupActivityName
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.startup.ui.hasRenderEvent
import io.embrace.android.embracesdk.internal.instrumentation.startup.ui.supportFrameCommitCallback
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
    private val destination: TelemetryDestination,
    private val versionChecker: VersionChecker,
    private val logger: InternalLogger,
    manualEnd: Boolean,
    processInfo: ProcessInfo,
) : AppStartupDataCollector {
    private val additionalTrackedIntervals = ConcurrentLinkedQueue<TrackedInterval>()
    private val customAttributes: MutableMap<String, String> = ConcurrentHashMap()
    private val trackRender = hasRenderEvent(versionChecker)
    private val trackFrameCommit = supportFrameCommitCallback(versionChecker)
    private val appStartupRootSpan = AtomicReference<SpanToken?>(null)
    private val dataCollectionComplete = AtomicBoolean(false)
    private val traceEnd = if (manualEnd) {
        TraceEnd.READY
    } else if (trackRender) {
        TraceEnd.RENDERED
    } else {
        TraceEnd.RESUMED
    }

    private val processCreatedMs: Long? = processInfo.startRequestedTimeMs()

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
    private var appStartupCompleteCallback: (() -> Unit)? = null

    @Volatile
    private var recordColdStart = true

    override fun applicationInitStart(timestampMs: Long?) {
        applicationInitStartMs = timestampMs ?: nowMs()
    }

    override fun applicationInitEnd(timestampMs: Long?) {
        applicationInitEndMs = timestampMs ?: nowMs()
    }

    override fun firstActivityInit(timestampMs: Long?, startupCompleteCallback: () -> Unit) {
        val activityInitTimeMs = timestampMs ?: nowMs()
        firstActivityInitStartMs = activityInitTimeMs
        appStartupCompleteCallback = startupCompleteCallback

        val sdkInitStartMs = startupServiceProvider()?.getSdkInitStartMs()
        val sdkInitEndMs = startupServiceProvider()?.getSdkInitEndMs()
        if (sdkInitStartMs != null && sdkInitEndMs != null) {
            applicationActivityCreationGap(sdkInitEndMs)?.let { gap ->
                recordColdStart = gap <= SDK_AND_ACTIVITY_INIT_GAP
                startTrace(
                    isColdStart = recordColdStart,
                    sdkInitStartMs = sdkInitStartMs,
                    activityInitTimeMs = activityInitTimeMs
                )
            }
        }
    }

    override fun startupActivityPreCreated(timestampMs: Long?) {
        startupActivityPreCreatedMs = timestampMs ?: nowMs()
    }

    override fun startupActivityInitStart(timestampMs: Long?) {
        startupActivityInitStartMs = timestampMs ?: nowMs()
    }

    override fun startupActivityPostCreated(timestampMs: Long?) {
        startupActivityPostCreatedMs = timestampMs ?: nowMs()
    }

    override fun startupActivityInitEnd(timestampMs: Long?) {
        startupActivityInitEndMs = timestampMs ?: nowMs()
    }

    override fun startupActivityResumed(
        activityName: String,
        timestampMs: Long?,
    ) {
        val timeMs = timestampMs ?: nowMs()
        startupActivityName = activityName
        startupActivityResumedMs = timeMs
        if (traceEnd == TraceEnd.RESUMED) {
            dataCollectionComplete(timeMs)
        }
    }

    override fun firstFrameRendered(
        activityName: String,
        timestampMs: Long?,
    ) {
        val timeMs = timestampMs ?: nowMs()
        startupActivityName = activityName
        firstFrameRenderedMs = timeMs
        if (traceEnd == TraceEnd.RENDERED) {
            dataCollectionComplete(timeMs)
        }
    }

    override fun appReady(timestampMs: Long?) {
        if (traceEnd == TraceEnd.READY) {
            dataCollectionComplete(timestampMs ?: nowMs())
        }
    }

    override fun addTrackedInterval(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<SpanEvent>,
        errorCode: ErrorCodeAttribute?,
    ) {
        additionalTrackedIntervals.add(
            TrackedInterval(
                name = name,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                attributes = attributes,
                events = events,
                errorCode = errorCode
            )
        )
    }

    override fun addAttribute(key: String, value: String) {
        customAttributes[key] = value
    }

    override fun onBackground() {
        dataCollectionComplete(clock.now(), false)
    }

    override fun onForeground() {
    }

    /**
     * Called when app startup is considered complete, i.e. the data can be used and any additional updates can be ignored
     */
    private fun dataCollectionComplete(traceEndTimeMs: Long, completed: Boolean = true) {
        if (!dataCollectionComplete.getAndSet(true)) {
            EmbTrace.trace("record-startup") {
                recordStartup(traceEndTimeMs, completed)
                if (appStartupRootSpan.get()?.isRecording() != false) {
                    logger.trackInternalError(
                        type = InternalErrorType.APP_LAUNCH_TRACE_FAIL,
                        throwable = IllegalStateException("App startup trace recording attempted but did not succeed")
                    )
                }
            }
            appStartupCompleteCallback?.invoke()
        }
    }

    private fun startTrace(isColdStart: Boolean, sdkInitStartMs: Long, activityInitTimeMs: Long) {
        val rootSpan = if (isColdStart) {
            val processStartTimeMs =
                if (versionChecker.isAtLeast(VERSION_CODES.N)) {
                    processCreatedMs
                } else if (applicationInitStartMs != null) {
                    applicationInitStartMs
                } else {
                    sdkInitStartMs
                }

            destination.startSpanCapture(
                name = COLD_APP_STARTUP_ROOT_SPAN,
                startTimeMs = processStartTimeMs ?: clock.now(),
            )
        } else {
            destination.startSpanCapture(
                name = WARM_APP_STARTUP_ROOT_SPAN,
                startTimeMs = activityInitTimeMs,
            )
        }

        if (rootSpan.isValid()) {
            appStartupRootSpan.set(rootSpan)
        } else {
            logger.trackInternalError(
                type = InternalErrorType.APP_LAUNCH_TRACE_FAIL,
                throwable = IllegalStateException("App startup trace could not be started")
            )
        }
    }

    private fun recordStartup(traceEndTimeMs: Long, completed: Boolean) {
        val startupService = startupServiceProvider() ?: return
        val sdkInitEndMs = startupService.getSdkInitEndMs()
        if (sdkInitEndMs != null) {
            val startupTrace = appStartupRootSpan.get()
            if (startupTrace != null) {
                val uiLoadedMs = if (trackRender) {
                    firstFrameRenderedMs
                } else {
                    startupActivityResumedMs
                }
                val activityInitStartMs = cappedBy(
                    value = startupActivityPreCreatedMs ?: startupActivityInitStartMs,
                    ceiling = uiLoadedMs
                )
                val activityInitEndMs = cappedBy(
                    value = startupActivityInitEndMs,
                    ceiling = uiLoadedMs
                )

                recordTrace(
                    applicationInitEndMs = if (recordColdStart) applicationInitEndMs else null,
                    sdkInitStartMs = if (recordColdStart) startupService.getSdkInitStartMs() else null,
                    sdkInitEndMs = if (recordColdStart) sdkInitEndMs else null,
                    firstActivityInitMs = firstActivityInitStartMs,
                    activityInitStartMs = activityInitStartMs,
                    activityInitEndMs = activityInitEndMs,
                    uiLoadedMs = uiLoadedMs,
                    traceEndTimeMs = traceEndTimeMs,
                    completed = completed,
                )
                recordAdditionalIntervals(startupTrace)
            }
        }
    }

    /**
     * Limit a value based on some ceiling if it is defined.
     * That is, return [ceiling] if it is non-null and less than [value].
     */
    private fun cappedBy(value: Long?, ceiling: Long?) =
        if (ceiling != null && (value == null || value > ceiling)) {
            ceiling
        } else {
            value
        }

    private fun recordAdditionalIntervals(startupTrace: SpanToken) {
        do {
            additionalTrackedIntervals.poll()?.let { trackedInterval ->
                destination.recordCompletedSpan(
                    name = trackedInterval.name,
                    startTimeMs = trackedInterval.startTimeMs,
                    endTimeMs = trackedInterval.endTimeMs,
                    parent = startupTrace,
                    internal = false,
                    attributes = trackedInterval.attributes,
                    events = trackedInterval.events,
                    errorCode = trackedInterval.errorCode
                )
            }
        } while (additionalTrackedIntervals.isNotEmpty())
    }

    @Suppress("CyclomaticComplexMethod", "ComplexMethod")
    private fun recordTrace(
        applicationInitEndMs: Long?,
        sdkInitStartMs: Long?,
        sdkInitEndMs: Long?,
        firstActivityInitMs: Long?,
        activityInitStartMs: Long?,
        activityInitEndMs: Long?,
        uiLoadedMs: Long?,
        traceEndTimeMs: Long,
        completed: Boolean,
    ) {
        appStartupRootSpan.get()?.takeIf { it.isRecording() }?.apply {
            addTraceMetadata()

            stop(
                errorCode = if (!completed) ErrorCodeAttribute.UserAbandon else null,
                endTimeMs = traceEndTimeMs
            )

            getStartTimeMs()?.let { traceStartTimeMs ->
                if (applicationInitEndMs != null) {
                    destination.recordCompletedSpan(
                        name = PROCESS_INIT_SPAN,
                        startTimeMs = traceStartTimeMs,
                        endTimeMs = applicationInitEndMs,
                        parent = this,
                    )
                }
            }

            if (sdkInitStartMs != null && sdkInitEndMs != null) {
                destination.recordCompletedSpan(
                    name = EMBRACE_INIT_SPAN,
                    startTimeMs = sdkInitStartMs,
                    endTimeMs = sdkInitEndMs,
                    parent = this,
                )
            }

            val lastEventBeforeActivityInit = applicationInitEndMs ?: sdkInitEndMs
            if (lastEventBeforeActivityInit != null && firstActivityInitMs != null) {
                destination.recordCompletedSpan(
                    name = ACTIVITY_INIT_DELAY_SPAN,
                    startTimeMs = lastEventBeforeActivityInit,
                    endTimeMs = firstActivityInitMs,
                    parent = this,
                )
            }

            if (activityInitStartMs != null && activityInitEndMs != null) {
                destination.recordCompletedSpan(
                    name = ACTIVITY_INIT_SPAN,
                    startTimeMs = activityInitStartMs,
                    endTimeMs = activityInitEndMs,
                    parent = this,
                )
            }

            if (activityInitEndMs != null && uiLoadedMs != null) {
                val uiLoadSpanName = if (trackRender) {
                    if (trackFrameCommit) {
                        ACTIVITY_RENDER_SPAN
                    } else {
                        ACTIVITY_FIRST_DRAW_SPAN
                    }
                } else {
                    ACTIVITY_LOAD_SPAN
                }
                destination.recordCompletedSpan(
                    name = uiLoadSpanName,
                    startTimeMs = activityInitEndMs,
                    endTimeMs = uiLoadedMs,
                    parent = this,
                )
            }

            if (traceEnd == TraceEnd.READY && uiLoadedMs != null && completed) {
                destination.recordCompletedSpan(
                    name = APP_READY_SPAN,
                    startTimeMs = uiLoadedMs,
                    endTimeMs = traceEndTimeMs,
                    parent = this,
                )
            }
        }
    }

    private fun applicationActivityCreationGap(sdkInitEndMs: Long): Long? =
        duration(applicationInitEndMs ?: sdkInitEndMs, firstActivityInitStartMs)

    private fun nowMs(): Long = clock.now()

    private fun SpanToken.addTraceMetadata() {
        addCustomAttributes()

        startupActivityName?.let { name ->
            setSystemAttribute(embStartupActivityName.name, name)
        }
    }

    private fun SpanToken.addCustomAttributes() {
        customAttributes.forEach {
            addAttribute(it.key, it.value)
        }
    }

    private data class TrackedInterval(
        val name: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val attributes: Map<String, String>,
        val events: List<SpanEvent>,
        val errorCode: ErrorCodeAttribute?,
    )

    private enum class TraceEnd {
        RESUMED,
        RENDERED,
        READY
    }

    companion object {
        /**
         * The gap between the end of the Embrace SDK initialization to when the first activity loaded after startup happens.
         * If this gap is greater than 2 seconds, it is assumed that the app process was not created because the user tapped on the app,
         * so we track this app launch as a warm start.
         */
        const val SDK_AND_ACTIVITY_INIT_GAP: Long = 2000L
        const val COLD_APP_STARTUP_ROOT_SPAN = "app-startup-cold"
        const val WARM_APP_STARTUP_ROOT_SPAN = "app-startup-warm"
        const val PROCESS_INIT_SPAN = "process-init"
        const val EMBRACE_INIT_SPAN = "embrace-init"
        const val ACTIVITY_INIT_DELAY_SPAN = "activity-init-delay"
        const val ACTIVITY_INIT_SPAN = "activity-init"
        const val ACTIVITY_RENDER_SPAN = "activity-render"
        const val ACTIVITY_FIRST_DRAW_SPAN = "activity-first-draw"
        const val ACTIVITY_LOAD_SPAN = "activity-load"
        const val APP_READY_SPAN = "app-ready"

        fun duration(start: Long?, end: Long?): Long? = if (start != null && end != null) {
            end - start
        } else {
            null
        }
    }
}
