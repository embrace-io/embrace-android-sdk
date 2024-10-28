package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.utils.lockAndRun
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Allows the tracking of [EmbraceSpan] instances so that their references can be retrieved with its associated spanId
 */
class SpanRepository {
    private val activeSpans: MutableMap<String, PersistableEmbraceSpan> = ConcurrentHashMap()
    private val completedSpans: MutableMap<String, PersistableEmbraceSpan> = mutableMapOf()
    private val spanIdsInProcess: MutableMap<String, AtomicInteger> = ConcurrentHashMap()
    private var spanUpdateNotifier: (() -> Unit)? = null

    /**
     * Track the [EmbraceSpan] if it has been started and it's not already tracked.
     */
    fun trackStartedSpan(embraceSpan: PersistableEmbraceSpan) {
        val spanId = embraceSpan.spanId ?: return

        if (notTracked(spanId)) {
            spanIdsInProcess.lockAndRun(spanId) {
                if (notTracked(spanId)) {
                    if (embraceSpan.isRecording) {
                        activeSpans[spanId] = embraceSpan
                    } else {
                        completedSpans[spanId] = embraceSpan
                    }
                }
            }
        }
    }

    /**
     * Transition active span to completed span if the span is tracked and the span is actually stopped.
     */
    fun trackedSpanStopped(spanId: String) {
        spanIdsInProcess.lockAndRun(spanId) {
            activeSpans[spanId]?.takeIf { !it.isRecording }?.let { activeSpans.remove(spanId) }?.let { embraceSpan ->
                completedSpans[spanId] = embraceSpan
            }
        }
    }

    /**
     * Return the [EmbraceSpan] with the corresponding [spanId] if it's tracked. Return null otherwise.
     */
    fun getSpan(spanId: String): EmbraceSpan? =
        spanIdsInProcess.lockAndRun(spanId) {
            activeSpans[spanId] ?: completedSpans[spanId]
        }

    /**
     * Get a list of active spans that are being tracked
     */
    fun getActiveSpans(): List<PersistableEmbraceSpan> = synchronized(spanIdsInProcess) {
        activeSpans.values.toList()
    }

    /**
     * Get a list of completed spans that are being tracked.
     */
    fun getCompletedSpans(): List<PersistableEmbraceSpan> = synchronized(spanIdsInProcess) {
        completedSpans.values.toList()
    }

    /**
     * Stop the existing active spans and mark them as failed
     */
    fun failActiveSpans(failureTimeMs: Long) {
        getActiveSpans().filterNot { it.hasFixedAttribute(EmbType.Ux.Session) }.forEach { span ->
            span.stop(ErrorCode.FAILURE, failureTimeMs)
        }
    }

    /**
     * Clear the spans this repository is tracking
     */
    fun clearCompletedSpans() {
        synchronized(spanIdsInProcess) {
            completedSpans.clear()
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

    private fun notTracked(spanId: String): Boolean = activeSpans[spanId] == null && completedSpans[spanId] == null
}
