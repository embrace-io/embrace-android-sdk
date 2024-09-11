package io.embrace.android.embracesdk.internal.capture.activity

import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.spans.PersistableEmbraceSpan
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.spans.ErrorCode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Observes Activity lifecycle and rendering events to create traces that model the workflow for showing an Activity on screen after
 * app startup has completed. This creates traces for both types of Activity opening specified in [OpenType].
 *
 * Depending on the version of Android and the state of the app, the start, end, and intermediate stages of the workflow will use
 * timestamps from different events, which affects the precision of the measurement.
 *
 * The start for [OpenType.COLD]:
 *
 * - On Android 10+, when [ActivityLifecycleCallbacks.onActivityPostPaused] is fired, denoting that the previous activity has completed
 *   its [ActivityLifecycleCallbacks.onActivityPaused] callbacks and a new Activity is ready to be created.
 *
 * - Android 9 and lower, when [ActivityLifecycleCallbacks.onActivityPaused] is fired, denoting that the previous activity is in the
 *   process of exiting. This will possibly result in some cleanup work of exiting the previous activity being included in the duration
 *   of the next trace that is logged.
 *
 *  The start for [OpenType.HOT]
 *
 * - On Android 10+, when [ActivityLifecycleCallbacks.onActivityPreStarted] is fired, denoting that an existing Activity instance is ready
 *   to be started
 *
 * - Android 9 and lower, when [ActivityLifecycleCallbacks.onActivityStarted] is fired, denoting that an existing activity is in the
 *   process of starting. This will possibly result in some of the work to start the activity already having happened depending on the
 *   other callbacks that have been registered.
 *
 * The end for both [OpenType.COLD] and [OpenType.HOT]:
 *
 * - Android 10+, when the Activity's first UI frame finishes rendering and is delivered to the screen
 *
 * - Android 9 and lower, when [ActivityLifecycleCallbacks.onActivityResumed] is fired.
 */
class OpenTraceEmitter(
    private val spanService: SpanService,
    private val versionChecker: VersionChecker,
) : OpenEvents {

    private val activeTraces: MutableMap<Int, ActivityOpenTrace> = ConcurrentHashMap()
    private val traceZygoteHolder: AtomicReference<OpenTraceZygote> = AtomicReference(INITIAL)
    private var currentTracedInstanceId: Int? = null

    override fun resetTrace(instanceId: Int, activityName: String, timestampMs: Long) {
        currentTracedInstanceId?.let { currentlyTracedInstanceId ->
            if (instanceId != currentlyTracedInstanceId) {
                endTrace(instanceId = currentlyTracedInstanceId, timestampMs = timestampMs, errorCode = ErrorCode.USER_ABANDON)
            }
        }
        traceZygoteHolder.set(
            OpenTraceZygote(
                lastActivityName = activityName,
                lastActivityInstanceId = instanceId,
                lastActivityPausedTimeMs = timestampMs
            )
        )
    }

    override fun hibernate(instanceId: Int, activityName: String, timestampMs: Long) {
        if (traceZygoteHolder.get().lastActivityInstanceId == instanceId) {
            traceZygoteHolder.set(BACKGROUNDED)
        }
    }

    override fun create(instanceId: Int, activityName: String, timestampMs: Long) {
        startTrace(
            openType = OpenType.COLD,
            instanceId = instanceId,
            activityName = activityName,
            timestampMs = timestampMs
        )
        startChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleEvent = LifecycleEvent.CREATE
        )
    }

    override fun createEnd(instanceId: Int, timestampMs: Long) {
        endChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleEvent = LifecycleEvent.CREATE
        )
    }

    override fun start(instanceId: Int, activityName: String, timestampMs: Long) {
        startTrace(
            openType = OpenType.HOT,
            instanceId = instanceId,
            activityName = activityName,
            timestampMs = timestampMs
        )
        startChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleEvent = LifecycleEvent.START
        )
    }

    override fun startEnd(instanceId: Int, timestampMs: Long) {
        endChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleEvent = LifecycleEvent.START
        )
    }

    override fun resume(instanceId: Int, activityName: String, timestampMs: Long) {
        if (!hasRenderEvent()) {
            endTrace(
                instanceId = instanceId,
                timestampMs = timestampMs,
            )
        } else {
            startChildSpan(
                instanceId = instanceId,
                timestampMs = timestampMs,
                lifecycleEvent = LifecycleEvent.RESUME
            )
        }
        traceZygoteHolder.set(READY)
    }

    override fun resumeEnd(instanceId: Int, timestampMs: Long) {
        endChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleEvent = LifecycleEvent.RESUME
        )
    }

    override fun render(instanceId: Int, activityName: String, timestampMs: Long) {
        startChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleEvent = LifecycleEvent.RENDER
        )
    }

    override fun renderEnd(instanceId: Int, timestampMs: Long) {
        endChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleEvent = LifecycleEvent.RENDER
        )
        endTrace(
            instanceId = instanceId,
            timestampMs = timestampMs,
        )
    }

    private fun startTrace(
        openType: OpenType,
        instanceId: Int,
        activityName: String,
        timestampMs: Long
    ) {
        if (traceZygoteHolder.get() == INITIAL) {
            return
        }

        if (!activeTraces.containsKey(instanceId)) {
            val zygote = traceZygoteHolder.getAndSet(READY)
            val startTimeMs = if (zygote.lastActivityPausedTimeMs != -1L) {
                zygote.lastActivityPausedTimeMs
            } else {
                timestampMs
            }

            spanService.startSpan(
                name = traceName(activityName, openType),
                type = EmbType.Performance.ActivityOpen,
                startTimeMs = startTimeMs,
            )?.let { root ->
                if (zygote.lastActivityInstanceId != -1) {
                    root.addSystemAttribute("last_activity", zygote.lastActivityName)
                }
                activeTraces[instanceId] = ActivityOpenTrace(root = root, activityName = activityName)
            }
        }
    }

    private fun endTrace(instanceId: Int, timestampMs: Long, errorCode: ErrorCode? = null) {
        activeTraces[instanceId]?.let { trace ->
            with(trace) {
                children.values.filter { it.isRecording }.forEach { span ->
                    span.stop(endTimeMs = timestampMs, errorCode = errorCode)
                }
                root.stop(endTimeMs = timestampMs, errorCode = errorCode)
            }
            activeTraces.remove(instanceId)
        }
    }

    private fun startChildSpan(instanceId: Int, timestampMs: Long, lifecycleEvent: LifecycleEvent) {
        val trace = activeTraces[instanceId]
        if (trace != null && !trace.children.containsKey(lifecycleEvent)) {
            spanService.startSpan(
                name = lifecycleEvent.spanName(trace.activityName),
                parent = trace.root,
                startTimeMs = timestampMs,
            )?.let { newSpan ->
                val newChildren = trace.children.plus(lifecycleEvent to newSpan)
                activeTraces[instanceId] = trace.copy(
                    children = newChildren
                )
            }
        }
    }

    private fun endChildSpan(instanceId: Int, timestampMs: Long, lifecycleEvent: LifecycleEvent) {
        activeTraces[instanceId]?.let { trace ->
            trace.children[lifecycleEvent]?.stop(timestampMs)
        }
    }

    private fun hasRenderEvent(): Boolean = versionChecker.isAtLeast(Build.VERSION_CODES.Q)

    private fun traceName(
        activityName: String,
        openType: OpenType
    ): String = "$activityName-${openType.typeName}-open"

    enum class LifecycleEvent(private val typeName: String) {
        CREATE("create"),
        START("start"),
        RESUME("resume"),
        RENDER("render");

        fun spanName(activityName: String): String = "$activityName-$typeName"
    }

    private data class ActivityOpenTrace(
        val activityName: String,
        val root: PersistableEmbraceSpan,
        val children: Map<LifecycleEvent, PersistableEmbraceSpan> = ConcurrentHashMap(),
    )

    private data class OpenTraceZygote(
        val lastActivityName: String,
        val lastActivityInstanceId: Int,
        val lastActivityPausedTimeMs: Long,
    )

    private companion object {
        const val INVALID_INSTANCE: Int = -1
        const val INVALID_TIME: Long = -1L

        val INITIAL = OpenTraceZygote(
            lastActivityName = "NEW_APP_LAUNCH",
            lastActivityInstanceId = INVALID_INSTANCE,
            lastActivityPausedTimeMs = INVALID_TIME
        )

        val READY = OpenTraceZygote(
            lastActivityName = "READY",
            lastActivityInstanceId = INVALID_INSTANCE,
            lastActivityPausedTimeMs = INVALID_TIME
        )

        val BACKGROUNDED = OpenTraceZygote(
            lastActivityName = "BACKGROUNDED",
            lastActivityInstanceId = INVALID_INSTANCE,
            lastActivityPausedTimeMs = INVALID_TIME
        )
    }
}
