package io.embrace.android.embracesdk.internal.capture.activity

import android.os.Build
import io.embrace.android.embracesdk.internal.spans.PersistableEmbraceSpan
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.ConcurrentHashMap

internal class LoadTraceEmitter(
    private val spanService: SpanService,
    private val backgroundWorker: BackgroundWorker,
    private val versionChecker: VersionChecker,
) : LoadEvents {

    private val activeTraces: MutableMap<Int, ActivityLoadTrace> = ConcurrentHashMap()

    override fun create(instanceId: Int, activityName: String, timestampMs: Long) {
        startTrace(
            openType = LoadEvents.OpenType.COLD,
            instanceId = instanceId,
            activityName = activityName,
            timestampMs = timestampMs
        )
        startChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            childType = ChildType.CREATE
        )
    }

    override fun createEnd(instanceId: Int, timestampMs: Long) {
        endChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            childType = ChildType.CREATE
        )
    }

    override fun start(instanceId: Int, activityName: String, timestampMs: Long) {
        startTrace(
            openType = LoadEvents.OpenType.HOT,
            instanceId = instanceId,
            activityName = activityName,
            timestampMs = timestampMs
        )
        startChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            childType = ChildType.START
        )
    }

    override fun startEnd(instanceId: Int, timestampMs: Long) {
        endChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            childType = ChildType.START
        )
    }

    override fun resume(instanceId: Int, timestampMs: Long) {
        startChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            childType = ChildType.RESUME
        )
    }

    override fun resumeEnd(instanceId: Int, timestampMs: Long) {
        endChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            childType = ChildType.RESUME
        )

        if (getEndEvent() == LoadEvents.EndEvent.RESUME) {
            endTrace(
                instanceId = instanceId,
                timestampMs = timestampMs,
            )
        }
    }

    override fun firstRender(instanceId: Int, timestampMs: Long) {
        startChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            childType = ChildType.RENDER
        )
    }

    override fun firstRenderEnd(instanceId: Int, timestampMs: Long) {
        endChildSpan(
            instanceId = instanceId,
            timestampMs = timestampMs,
            childType = ChildType.RENDER
        )
        endTrace(
            instanceId = instanceId,
            timestampMs = timestampMs,
        )
    }

    private fun startTrace(
        openType: LoadEvents.OpenType,
        instanceId: Int,
        activityName: String,
        timestampMs: Long
    ) {
        if (!activeTraces.containsKey(instanceId)) {
            spanService.startSpan(
                name = traceName(activityName, openType),
                startTimeMs = timestampMs,
            )?.let { root ->
                activeTraces[instanceId] = ActivityLoadTrace(root = root, activityName = activityName)
            }
        }
    }

    private fun endTrace(instanceId: Int, timestampMs: Long) {
        activeTraces[instanceId]?.let { trace ->
            backgroundWorker.submit {
                trace.root.stop(timestampMs)
                activeTraces.remove(instanceId)
            }
        }
    }

    private fun startChildSpan(instanceId: Int, timestampMs: Long, childType: ChildType) {
        val trace = activeTraces[instanceId]
        if (trace != null && !trace.children.containsKey(childType)) {
            backgroundWorker.submit {
                spanService.startSpan(
                    name = childType.spanName(trace.activityName),
                    parent = trace.root,
                    startTimeMs = timestampMs,
                )?.let { newSpan ->
                    activeTraces[instanceId] = trace.copy(
                        children = trace.children.plus(childType to newSpan)
                    )
                }
            }
        }
    }

    private fun endChildSpan(instanceId: Int, timestampMs: Long, childType: ChildType) {
        activeTraces[instanceId]?.let { trace ->
            backgroundWorker.submit {
                trace.children[childType]?.stop(timestampMs)
            }
        }
    }

    private fun getEndEvent(): LoadEvents.EndEvent {
        return if (versionChecker.isAtLeast(Build.VERSION_CODES.Q)) {
            LoadEvents.EndEvent.RENDER
        } else {
            LoadEvents.EndEvent.RESUME
        }
    }

    private fun traceName(
        activityName: String,
        openType: LoadEvents.OpenType
    ): String = "$activityName-${openType.typeName}-${getEndEvent().eventName}"

    private enum class ChildType(val typeName: String) {
        CREATE("create"),
        START("start"),
        RESUME("resume"),
        RENDER("render");

        fun spanName(activityName: String): String = "$activityName-$typeName"
    }

    private data class ActivityLoadTrace(
        val activityName: String,
        val root: PersistableEmbraceSpan,
        val children: Map<ChildType, PersistableEmbraceSpan> = emptyMap(),
    )
}
