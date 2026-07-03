package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Allows the tracking of [EmbraceSpan] instances so that their references can be retrieved with its associated spanId
 */
class SpanRepository {
    private val spans: ConcurrentMap<String, EmbraceSdkSpan> = ConcurrentHashMap()
    private var spanUpdateNotifier: (() -> Unit)? = null

    /**
     * Track the [EmbraceSpan] if it has been started and it's not already tracked.
     */
    fun trackStartedSpan(embraceSpan: EmbraceSdkSpan) {
        val spanId = embraceSpan.spanId ?: return
        spans.putIfAbsent(spanId, embraceSpan)
    }

    /**
     * Return the [EmbraceSdkSpan] with the corresponding [spanId] if it's tracked. Return null otherwise.
     */
    fun getSpan(spanId: String): EmbraceSdkSpan? = spans[spanId]

    /**
     * Get a list of active spans that are being tracked
     */
    fun getActiveSpans(): List<EmbraceSdkSpan> {
        return spans.values.filter { it.isRecording }
    }

    /**
     * Get a list of completed spans that are being tracked.
     */
    fun getCompletedSpans(): List<EmbraceSdkSpan> {
        return spans.values.filterNot { it.isRecording }
    }

    /**
     * Stop the existing active spans and mark them as failed
     */
    fun failActiveSpans(failureTimeMs: Long) {
        getActiveSpans().filterNot { it.hasEmbraceAttribute(EmbType.Ux.Session) }.forEach { span ->
            span.stop(ErrorCode.FAILURE, failureTimeMs)
        }
    }

    /**
     * Clear the completed spans this repository is tracking
     */
    fun clearCompletedSpans() {
        val iterator = spans.values.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            if (!candidate.isRecording) {
                iterator.remove()
            }
        }
    }

    /**
     * Set a function to be invoked when a span has been updated
     */
    fun setSpanUpdateNotifier(notifier: () -> Unit) {
        spanUpdateNotifier = notifier
    }

    /**
     * Call to notify the repository that a span has been updated
     */
    fun notifySpanUpdate() {
        spanUpdateNotifier?.invoke()
    }

    /**
     * Automatically terminates root spans
     */
    fun autoTerminateSpans(now: Long) {
        val roots = buildSpanTree()
        terminateSpansIfRequired(now, roots.filter { it.span.autoTerminationMode == AutoTerminationMode.ON_BACKGROUND })
    }

    /**
     * Terminates any spans & their descendants that are set to auto terminate on the process entering the background.
     *
     * The root span and their descendants are terminated via depth-first traversal. The end time is guaranteed
     * to be the same for any auto-terminated spans.
     */
    private fun terminateSpansIfRequired(endTimeMs: Long, nodes: List<SpanNode>) {
        nodes.forEach { node ->
            if (node.span.isRecording) {
                node.span.stop(endTimeMs = endTimeMs)
            }
            terminateSpansIfRequired(endTimeMs, node.children)
        }
    }

    private fun buildSpanTree(): List<SpanNode> {
        // first, create nodes individually by getting all active spans, then adding them to all completed spans
        val allSpans = spans.values
        val nodes = allSpans.map { SpanNode(it, mutableListOf()) }.associateBy(SpanNode::span)
        val roots = mutableListOf<SpanNode>()

        // then build relationships between nodes
        allSpans.forEach { span ->
            nodes[span]?.let { node ->
                if (span.parent != null) {
                    nodes[span.parent]?.children?.add(node)
                } else {
                    roots.add(node)
                }
            }
        }
        // finally, return a list of root nodes
        return roots.toList()
    }

    private data class SpanNode(
        val span: EmbraceSpan,
        val children: MutableList<SpanNode>,
    )
}
