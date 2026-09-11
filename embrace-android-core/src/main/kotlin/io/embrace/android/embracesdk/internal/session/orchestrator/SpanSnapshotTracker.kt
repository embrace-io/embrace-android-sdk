package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the spans that are recording, and those among them whose latest state has yet to reach
 * disk.
 */
class SpanSnapshotTracker {

    private val inFlight: MutableSet<EmbraceSdkSpan> = Collections.newSetFromMap(ConcurrentHashMap())
    private val dirty: MutableSet<EmbraceSdkSpan> = Collections.newSetFromMap(ConcurrentHashMap())

    /**
     * Every span known to be recording.
     */
    fun inFlightSpans(): List<EmbraceSdkSpan> = inFlight.toList()

    /**
     * Records that [span] has changed.
     */
    fun onSpanChanged(span: EmbraceSdkSpan) {
        if (span.isSessionSpan()) {
            return
        }
        if (span.isRecording) {
            inFlight.add(span)
            dirty.add(span)
        } else {
            inFlight.remove(span)
            dirty.remove(span)
        }
    }

    /**
     * Tracks the spans that were already recording before any change was observed, marking them as
     * yet to reach disk.
     */
    fun seed(spans: List<EmbraceSdkSpan>) {
        inFlight.removeAll { !it.isRecording }
        dirty.removeAll { !it.isRecording }
        spans.filter { it.isRecording && !it.isSessionSpan() }.forEach { span ->
            inFlight.add(span)
            dirty.add(span)
        }
    }

    /**
     * Takes the spans that have changed, leaving the set empty for the changes that follow.
     */
    fun drainDirtySpans(): List<EmbraceSdkSpan> {
        val drained = mutableListOf<EmbraceSdkSpan>()
        val spans = dirty.iterator()
        while (spans.hasNext()) {
            drained.add(spans.next())
            spans.remove()
        }
        return drained
    }

    private fun EmbraceSdkSpan.isSessionSpan(): Boolean = hasEmbraceAttribute(EmbType.Ux.Session)
}
