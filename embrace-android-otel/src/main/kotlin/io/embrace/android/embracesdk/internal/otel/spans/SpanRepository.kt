package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.threadSafeToList
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap

/**
 * Stores the spans of the current session. Two distinct representations are held:
 *
 * - [EmbraceSdkSpan] instances: the in-progress and completed Embrace span objects, tracked so their
 *   references can be retrieved by their associated spanId. Accessed via the `*EmbraceSpan(s)` methods.
 * - [Span]: the completed spans exported through the OTel pipeline that are pending delivery
 *   off-device. Accessed via the `*OtelSpan(s)` methods.
 */
class SpanRepository {
    private val spans: ConcurrentMap<String, EmbraceSdkSpan> = ConcurrentHashMap()
    private var spanUpdateNotifier: (() -> Unit)? = null

    private val completedSpanData: Queue<Span> = ConcurrentLinkedQueue()
    private val flushLock = Any()

    /**
     * Track the [EmbraceSpan] if it has been started and it's not already tracked.
     */
    fun trackStartedEmbraceSpan(embraceSpan: EmbraceSdkSpan) {
        val spanId = embraceSpan.spanId ?: return
        spans.putIfAbsent(spanId, embraceSpan)
    }

    /**
     * Return the [EmbraceSdkSpan] with the corresponding [spanId] if it's tracked. Return null otherwise.
     */
    fun getEmbraceSpan(spanId: String): EmbraceSdkSpan? = spans[spanId]

    /**
     * Get a list of active spans that are being tracked
     */
    fun getActiveEmbraceSpans(): List<EmbraceSdkSpan> {
        return spans.values.filter { it.isRecording }
    }

    /**
     * Get a list of completed spans that are being tracked.
     */
    fun getCompletedEmbraceSpans(): List<EmbraceSdkSpan> {
        return spans.values.filterNot { it.isRecording }
    }

    /**
     * Stop any active span whose timeout deadline has passed, marking it as failed. The span's end
     * time is set to its deadline (start + timeout) so its duration reflects the configured timeout
     * regardless of when the sweep runs. Spans with no timeout are untouched.
     */
    fun stopTimedOutSpans(now: Long) {
        getActiveEmbraceSpans().forEach { span ->
            val mode = span.terminationMode
            val startTimeMs = span.getStartTimeMs()
            if (mode is SpanTerminationMode.Timeout && startTimeMs != null) {
                val deadlineMs = startTimeMs + mode.timeoutMs
                if (now >= deadlineMs) {
                    span.stopWithErrorCode(ErrorCodeAttribute.Failure, deadlineMs)
                }
            }
        }
    }

    /**
     * Stop the existing active spans and mark them as failed
     */
    fun failActiveEmbraceSpans(failureTimeMs: Long) {
        getActiveEmbraceSpans().filterNot { it.hasEmbraceAttribute(EmbType.Ux.Session) }.forEach { span ->
            span.stopWithErrorCode(ErrorCodeAttribute.Failure, failureTimeMs)
        }
    }

    /**
     * Clear the completed spans this repository is tracking
     */
    fun clearCompletedEmbraceSpans() {
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
    fun autoTerminateEmbraceSpans(now: Long) {
        val roots = buildSpanTree()
        terminateSpansIfRequired(now, roots.filter { it.span.autoTerminationMode == AutoTerminationMode.ON_BACKGROUND })
    }

    /**
     * Stores [Span] for spans that have been completed and exported. Supports concurrent invocations.
     */
    fun storeCompletedOtelSpans(spans: List<Span>): StoreDataResult {
        try {
            completedSpanData += spans
        } catch (t: Throwable) {
            return StoreDataResult.FAILURE
        }
        return StoreDataResult.SUCCESS
    }

    /**
     * Returns the list of the currently stored completed [Span].
     */
    fun completedOtelSpans(): List<Span> = completedSpanData.threadSafeToList()

    /**
     * Returns and clears the currently stored completed [Span]. The clearing and returning is
     * atomic, i.e. spans cannot be added during this operation.
     */
    fun flushOtelSpans(): List<Span> {
        synchronized(flushLock) {
            val count = completedSpanData.size
            val flushed = ArrayList<Span>(count)
            repeat(count) { completedSpanData.poll()?.let(flushed::add) }
            return flushed
        }
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
