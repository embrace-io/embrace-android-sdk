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
 * Observes [UiLoadEventListener] to create traces that model the workflow for displaying UI on screen.
 * This will record traces for all [UiLoadType] but will ignore any UI load that is part of the app startup workflow.
 *
 * Depending on the version of Android and the state of the app, the start, end, and intermediate stages of the workflow will use
 * timestamps from different events, which affects the precision of the measurements as well as the child spans contained in the trace.
 *
 * An assumption that there can only be one activity going through its the activity lifecycle at a time. If we see events
 * that come from a different Activity instance than the last one processed, we assume that last one's loading has been
 * interrupted so any load traces associated with it can be abandoned.
 *
 * The start for [UiLoadType.COLD]:
 *
 * - On Android 10+, when [ActivityLifecycleCallbacks.onActivityPreCreated] is fired, denoting the activity is
 *   ready to be created. This timestamp will be before any onCreate callbacks are run.
 *
 * - Android 9 and lower, when [ActivityLifecycleCallbacks.onActivityCreated] is fired, denoting the activity is in the
 *   process of being created. The timestamp will vary depending on which onCreate callback have already run.
 *
 *  The start for [UiLoadType.HOT]
 *
 * - On Android 10+, when [ActivityLifecycleCallbacks.onActivityPreStarted] is fired, denoting that an existing Activity instance is ready
 *   to be started
 *
 * - Android 9 and lower, when [ActivityLifecycleCallbacks.onActivityStarted] is fired, denoting that an existing activity is in the
 *   process of starting. This will possibly result in some of the work to start the activity already having happened depending on the
 *   other callbacks that have been registered.
 *
 * The end for both [UiLoadType.COLD] and [UiLoadType.HOT]:
 *
 * - Android 10+, when the Activity's first UI frame finishes rendering and is delivered to the screen
 *
 * - Android 9 and lower, when [ActivityLifecycleCallbacks.onActivityResumed] is fired.
 */
class UiLoadTraceEmitter(
    private val spanService: SpanService,
    private val versionChecker: VersionChecker,
) : UiLoadEventListener {

    private val activeTraces: MutableMap<Int, UiLoadTrace> = ConcurrentHashMap()
    private var currentInstance: AtomicReference<UiInstance?> = AtomicReference()

    override fun create(instanceId: Int, activityName: String, timestampMs: Long, manualEnd: Boolean) {
        startTrace(
            uiLoadType = UiLoadType.COLD,
            instanceId = instanceId,
            activityName = activityName,
            timestampMs = timestampMs,
            manualEnd = manualEnd
        )
        startChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleStage = LifecycleStage.CREATE
        )
    }

    override fun createEnd(instanceId: Int, timestampMs: Long) {
        endChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleStage = LifecycleStage.CREATE
        )
    }

    override fun start(instanceId: Int, activityName: String, timestampMs: Long, manualEnd: Boolean) {
        startTrace(
            uiLoadType = UiLoadType.HOT,
            instanceId = instanceId,
            activityName = activityName,
            timestampMs = timestampMs,
            manualEnd = manualEnd
        )
        startChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleStage = LifecycleStage.START
        )
    }

    override fun startEnd(instanceId: Int, timestampMs: Long) {
        endChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleStage = LifecycleStage.START
        )
    }

    override fun resume(instanceId: Int, activityName: String, timestampMs: Long) {
        if (traceCompleteTrigger(instanceId) == TraceCompleteTrigger.RESUME) {
            endTrace(
                instanceId = instanceId,
                timestampMs = timestampMs,
            )
        } else if (hasRenderEvent()) {
            startChildSpan(
                instanceId = instanceId,
                timestampMs = timestampMs,
                lifecycleStage = LifecycleStage.RESUME
            )
        }
    }

    override fun resumeEnd(instanceId: Int, timestampMs: Long) {
        endChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleStage = LifecycleStage.RESUME
        )
    }

    override fun render(instanceId: Int, activityName: String, timestampMs: Long) {
        startChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleStage = LifecycleStage.RENDER
        )
    }

    override fun renderEnd(instanceId: Int, timestampMs: Long) {
        endChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            lifecycleStage = LifecycleStage.RENDER
        )

        if (traceCompleteTrigger(instanceId) == TraceCompleteTrigger.RENDER) {
            endTrace(
                instanceId = instanceId,
                timestampMs = timestampMs,
            )
        }
    }

    override fun complete(instanceId: Int, timestampMs: Long) {
        if (traceCompleteTrigger(instanceId) == TraceCompleteTrigger.MANUAL) {
            endTrace(
                instanceId = instanceId,
                timestampMs = timestampMs,
            )
        }
    }

    override fun exit(instanceId: Int, activityName: String, timestampMs: Long) {
        // end trace as abandoned if it's not already complete
        endTrace(
            instanceId = instanceId,
            timestampMs = timestampMs,
            errorCode = ErrorCode.USER_ABANDON
        )
    }

    override fun reset(lastInstanceId: Int) {
    }

    private fun startTrace(
        uiLoadType: UiLoadType,
        instanceId: Int,
        activityName: String,
        timestampMs: Long,
        manualEnd: Boolean,
    ) {
        if (!activeTraces.containsKey(instanceId)) {
            val newInstance = UiInstance(activityName, instanceId)
            val previousInstance = currentInstance.getAndSet(newInstance)
            if (previousInstance != null) {
                exit(previousInstance.id, previousInstance.name, timestampMs)
            }

            spanService.startSpan(
                name = traceName(activityName, uiLoadType),
                type = EmbType.Performance.UiLoad,
                startTimeMs = timestampMs,
            )?.let { root ->
                activeTraces[instanceId] = UiLoadTrace(
                    root = root,
                    traceCompleteTrigger = determineEndEvent(manualEnd),
                    activityName = activityName
                )
            }
        }
    }

    private fun determineEndEvent(manualEnd: Boolean): TraceCompleteTrigger {
        return if (manualEnd) {
            TraceCompleteTrigger.MANUAL
        } else if (hasRenderEvent()) {
            TraceCompleteTrigger.RENDER
        } else {
            TraceCompleteTrigger.RESUME
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

    private fun startChildSpan(instanceId: Int, timestampMs: Long, lifecycleStage: LifecycleStage) {
        val trace = activeTraces[instanceId]
        if (trace != null && !trace.children.containsKey(lifecycleStage)) {
            spanService.startSpan(
                name = lifecycleStage.spanName(trace.activityName),
                parent = trace.root,
                startTimeMs = timestampMs,
            )?.let { newSpan ->
                val newChildren = trace.children.plus(lifecycleStage to newSpan)
                activeTraces[instanceId] = trace.copy(
                    children = newChildren
                )
            }
        }
    }

    private fun endChildSpan(instanceId: Int, timestampMs: Long, lifecycleStage: LifecycleStage) {
        activeTraces[instanceId]?.let { trace ->
            trace.children[lifecycleStage]?.stop(timestampMs)
        }
    }

    private fun traceCompleteTrigger(instanceId: Int): TraceCompleteTrigger? =
        activeTraces[instanceId]?.traceCompleteTrigger

    private fun hasRenderEvent(): Boolean = versionChecker.isAtLeast(Build.VERSION_CODES.Q)

    private fun traceName(
        activityName: String,
        uiLoadType: UiLoadType,
    ): String = "$activityName-${uiLoadType.typeName}-time-to-initial-display"

    /**
     * Metadata for the trace recorded for a particular instance of UI Load
     */
    private data class UiLoadTrace(
        val activityName: String,
        val traceCompleteTrigger: TraceCompleteTrigger,
        val root: PersistableEmbraceSpan,
        val children: Map<LifecycleStage, PersistableEmbraceSpan> = ConcurrentHashMap(),
    )

    private data class UiInstance(val name: String, val id: Int)

    /**
     * The trigger to end a particular UI Load trace.
     */
    enum class TraceCompleteTrigger {
        RESUME,
        RENDER,
        MANUAL,
    }
}
