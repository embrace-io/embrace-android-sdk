package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.spans.EmbraceSpan
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Allows the tracking of [EmbraceSpan] instances so that their references can be retrieved with its associated spanId
 */
internal class SpansRepository {
    private val activeSpans: MutableMap<String, EmbraceSpan> = mutableMapOf()
    private val completedSpans: MutableMap<String, EmbraceSpan> = mutableMapOf()
    private val clearing = AtomicBoolean(false)

    /**
     * Track the [EmbraceSpan] if it has been started and it's not already tracked.
     */
    fun trackStartedSpan(embraceSpan: EmbraceSpan) {
        val spanId = embraceSpan.spanId ?: return
        if (notTracked(spanId)) {
            synchronized(activeSpans) {
                if (notTracked(spanId)) {
                    if (embraceSpan.isRecording) {
                        activeSpans[spanId] = embraceSpan
                    } else {
                        setCompletedSpan(spanId, embraceSpan)
                    }
                }
            }
        }
    }

    /**
     * Transition active span to completed span if the span is tracked and the span is actually stopped.
     */
    fun trackedSpanStopped(spanId: String) {
        synchronized(activeSpans) {
            activeSpans[spanId]?.takeIf { !it.isRecording }?.let { activeSpans.remove(spanId) }?.let { embraceSpan ->
                setCompletedSpan(spanId, embraceSpan)
            }
        }
    }

    /**
     * Return the [EmbraceSpan] with the corresponding [spanId] if it's tracked. Return null otherwise.
     */
    fun getSpan(spanId: String): EmbraceSpan? {
        synchronized(activeSpans) {
            activeSpans[spanId]?.let { return it }
        }
        return getCompletedSpan(spanId)
    }

    /**
     * Get a list of active spans that are being tracked
     */
    fun getActiveSpans(): List<EmbraceSpan> = synchronized(activeSpans) { activeSpans.values.toList() }

    /**
     * Get a list of completed spans that are being tracked.
     */
    fun getCompletedSpans(): List<EmbraceSpan> = synchronized(completedSpans) { completedSpans.values.toList() }

    fun clearCompletedSpans() {
        synchronized(completedSpans) {
            clearing.set(true)
            completedSpans.clear()
            clearing.set(false)
        }
    }

    /**
     * Prevent locking and blocking on a get, always return null if we are clearing completed spans
     */
    private fun getCompletedSpan(spanId: String): EmbraceSpan? {
        return if (!clearing.get()) {
            completedSpans[spanId]
        } else {
            null
        }
    }

    private fun setCompletedSpan(spanId: String, span: EmbraceSpan) {
        synchronized(completedSpans) {
            completedSpans[spanId] = span
        }
    }

    private fun notTracked(spanId: String): Boolean =
        synchronized(activeSpans) { activeSpans[spanId] == null && getCompletedSpan(spanId) == null }
}
