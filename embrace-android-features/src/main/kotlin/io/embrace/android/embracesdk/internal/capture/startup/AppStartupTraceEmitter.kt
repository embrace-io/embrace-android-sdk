package io.embrace.android.embracesdk.internal.capture.startup

import android.os.Build.VERSION_CODES
import android.os.Process
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.opentelemetry.embStartupActivityName
import io.embrace.android.embracesdk.internal.spans.PersistableEmbraceSpan
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.sdk.common.Clock
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
    private val spanService: SpanService,
    private val backgroundWorker: BackgroundWorker,
    private val versionChecker: VersionChecker,
    private val logger: EmbLogger,
    manualEnd: Boolean = false,
) : AppStartupDataCollector {
    private val processCreateRequestedMs: Long?
    private val processCreatedMs: Long?
    private val additionalTrackedIntervals = ConcurrentLinkedQueue<TrackedInterval>()
    private val customAttributes: MutableMap<String, String> = ConcurrentHashMap()
    private val hasRenderEvent = startupHasRenderEvent(versionChecker)
    private val appStartupRootSpan = AtomicReference<PersistableEmbraceSpan?>(null)
    private val dataCollectionComplete = AtomicBoolean(false)
    private val traceEnd = if (manualEnd) {
        TraceEnd.READY
    } else if (hasRenderEvent) {
        TraceEnd.RENDERED
    } else {
        TraceEnd.RESUMED
    }

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
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
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

    /**
     * Called when app startup is considered complete, i.e. the data can be used and any additional updates can be ignored
     */
    private fun dataCollectionComplete(traceEndTimeMs: Long) {
        if (!dataCollectionComplete.getAndSet(true)) {
            backgroundWorker.submit {
                recordStartup(traceEndTimeMs)
                if (appStartupRootSpan.get()?.isRecording != false) {
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

            spanService.startSpan(
                name = COLD_APP_STARTUP_ROOT_SPAN,
                startTimeMs = processStartTimeMs,
            )
        } else {
            spanService.startSpan(
                name = WARM_APP_STARTUP_ROOT_SPAN,
                startTimeMs = activityInitTimeMs,
            )
        }

        if (rootSpan != null) {
            appStartupRootSpan.set(rootSpan)
        } else {
            logger.trackInternalError(
                type = InternalErrorType.APP_LAUNCH_TRACE_FAIL,
                throwable = IllegalStateException("App startup trace could not be started")
            )
        }
    }

    private fun recordStartup(traceEndTimeMs: Long) {
        val startupService = startupServiceProvider() ?: return
        val sdkInitEndMs = startupService.getSdkInitEndMs()
        if (sdkInitEndMs != null) {
            appStartupRootSpan.get()?.let { startupTrace ->
                recordTtid(
                    applicationInitEndMs = if (recordColdStart) applicationInitEndMs else null,
                    sdkInitStartMs = if (recordColdStart) startupService.getSdkInitStartMs() else null,
                    sdkInitEndMs = if (recordColdStart) sdkInitEndMs else null,
                    firstActivityInitMs = firstActivityInitStartMs,
                    activityInitStartMs = startupActivityPreCreatedMs ?: startupActivityInitStartMs,
                    activityInitEndMs = startupActivityInitEndMs,
                    uiLoadedMs = if (hasRenderEvent) firstFrameRenderedMs else startupActivityResumedMs,
                    traceEndTimeMs = traceEndTimeMs
                )
                recordAdditionalIntervals(startupTrace)
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
                    internal = false,
                    attributes = trackedInterval.attributes,
                    events = trackedInterval.events,
                    errorCode = trackedInterval.errorCode
                )
            }
        } while (additionalTrackedIntervals.isNotEmpty())
    }

    @Suppress("CyclomaticComplexMethod", "ComplexMethod")
    private fun recordTtid(
        applicationInitEndMs: Long?,
        sdkInitStartMs: Long?,
        sdkInitEndMs: Long?,
        firstActivityInitMs: Long?,
        activityInitStartMs: Long?,
        activityInitEndMs: Long?,
        uiLoadedMs: Long?,
        traceEndTimeMs: Long,
    ) {
        appStartupRootSpan.get()?.takeIf { it.isRecording }?.apply {
            addTraceMetadata()

            stop(endTimeMs = traceEndTimeMs)

            getStartTimeMs()?.let { traceStartTimeMs ->
                if (applicationInitEndMs != null) {
                    spanService.recordCompletedSpan(
                        name = PROCESS_INIT_SPAN,
                        parent = this,
                        startTimeMs = traceStartTimeMs,
                        endTimeMs = applicationInitEndMs,
                    )
                }
            }

            if (sdkInitStartMs != null && sdkInitEndMs != null) {
                spanService.recordCompletedSpan(
                    name = EMBRACE_INIT_SPAN,
                    parent = this,
                    startTimeMs = sdkInitStartMs,
                    endTimeMs = sdkInitEndMs,
                )
            }

            val lastEventBeforeActivityInit = applicationInitEndMs ?: sdkInitEndMs
            if (lastEventBeforeActivityInit != null && firstActivityInitMs != null) {
                spanService.recordCompletedSpan(
                    name = ACTIVITY_INIT_DELAY_SPAN,
                    parent = this,
                    startTimeMs = lastEventBeforeActivityInit,
                    endTimeMs = firstActivityInitMs,
                )
            }

            if (activityInitStartMs != null && activityInitEndMs != null) {
                spanService.recordCompletedSpan(
                    name = ACTIVITY_INIT_SPAN,
                    parent = this,
                    startTimeMs = activityInitStartMs,
                    endTimeMs = activityInitEndMs,
                )
            }

            if (activityInitEndMs != null && uiLoadedMs != null) {
                val uiLoadSpanName = if (hasRenderEvent) {
                    ACTIVITY_RENDER_SPAN
                } else {
                    ACTIVITY_LOAD_SPAN
                }
                spanService.recordCompletedSpan(
                    name = uiLoadSpanName,
                    parent = this,
                    startTimeMs = activityInitEndMs,
                    endTimeMs = uiLoadedMs,
                )
            }

            if (traceEnd == TraceEnd.READY && uiLoadedMs != null) {
                spanService.recordCompletedSpan(
                    name = APP_READY_SPAN,
                    parent = this,
                    startTimeMs = uiLoadedMs,
                    endTimeMs = traceEndTimeMs,
                )
            }
        }
    }

    private fun applicationActivityCreationGap(sdkInitEndMs: Long): Long? =
        duration(applicationInitEndMs ?: sdkInitEndMs, firstActivityInitStartMs)

    private fun nowMs(): Long = clock.now().nanosToMillis()

    private fun PersistableEmbraceSpan.addTraceMetadata() {
        addCustomAttributes()

        startupActivityName?.let { name ->
            setSystemAttribute(embStartupActivityName.attributeKey, name)
        }
    }

    private fun PersistableEmbraceSpan.addCustomAttributes() {
        customAttributes.forEach {
            addAttribute(it.key, it.value)
        }
    }

    private data class TrackedInterval(
        val name: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val attributes: Map<String, String>,
        val events: List<EmbraceSpanEvent>,
        val errorCode: ErrorCode?,
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
        const val ACTIVITY_LOAD_SPAN = "activity-load"
        const val APP_READY_SPAN = "app-ready"

        fun duration(start: Long?, end: Long?): Long? = if (start != null && end != null) {
            end - start
        } else {
            null
        }

        fun startupHasRenderEvent(versionChecker: VersionChecker) = versionChecker.isAtLeast(VERSION_CODES.Q)
    }
}
