package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the spans that are recording. What has yet to reach disk is held by the queue the changed
 * spans are submitted to.
 */
class SpanSnapshotTracker {

    private val inFlight: MutableSet<EmbraceSdkSpan> = Collections.newSetFromMap(ConcurrentHashMap())

    /**
     * Every span known to be recording.
     */
    fun inFlightSpans(): List<EmbraceSdkSpan> = inFlight.toList()

    /**
     * Records that [span] has changed.
     */
    fun onSpanChanged(span: EmbraceSdkSpan) {
        if (span.isRecording) {
            inFlight.add(span)
        } else {
            inFlight.remove(span)
        }
    }

    /**
     * Tracks the spans that were already recording before any change was observed.
     */
    fun seed(spans: List<EmbraceSdkSpan>) {
        inFlight.removeAll { !it.isRecording }
        inFlight.addAll(spans.filter { it.isRecording })
    }
}
