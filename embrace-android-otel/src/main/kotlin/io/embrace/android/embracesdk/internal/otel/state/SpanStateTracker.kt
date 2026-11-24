package io.embrace.android.embracesdk.internal.otel.state

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan
import java.util.concurrent.ConcurrentHashMap

/**
 * Links every span to the state changes around it: [StateAttributes.SEQUENCE] when it starts, and
 * [StateAttributes.END_SEQUENCE] when it ends if a state changed while it ran.
 *
 * The two numbers are the ones that held at the times the span has, which are not always the times it is started and
 * ended at: a span can be given a start time or an end time from the past, and a span recorded once it is over is given
 * both. So, for each end of a span:
 *
 * - If its time is not from before the current [StateStamp] took effect, no state has changed since that time, and the
 *   current stamp is the one that held. This is every span that starts or ends now, and it reads one reference.
 * - Otherwise a state has changed since, and [stateAt] is asked for the stamp that held at that time. It knows for as far
 *   back as whatever records state remembers.
 *
 * A span whose start is not known is not linked, and carries neither attribute. A span whose end is not known keeps the
 * number it started with and gets none for its end, which reads as no state having changed while it ran. Its start is
 * still right, a number written on a span cannot be taken back, and nothing better can be known of its end.
 *
 * Every time is taken to be one the SDK's clock gave, as the times of state changes are. A span given a time read from
 * another clock is placed where that reading falls on the SDK's clock.
 *
 * What is kept for a span in between is a reference to a stamp, so nothing here grows with the number of states.
 *
 * @param stateProvider the current stamp, or null while nothing records state.
 * @param stateAt the stamp that held at a time in the past, in milliseconds, or null if that is not known.
 * @param keepUntilReleased whether what is kept for a span outlives its ending, until [release], so that [withValues]
 * can put the values on what is exported of it.
 */
internal class SpanStateTracker(
    private val stateProvider: () -> StateStamp?,
    private val stateAt: (timeMs: Long) -> StateStamp?,
    private val keepUntilReleased: Boolean,
) : SpanStateLink {

    /**
     * The stamp that held when each linked span in progress started, by span id.
     */
    private val starts = ConcurrentHashMap<String, StateStamp>()

    /**
     * The stamp that held when a span ended, by span id, for a span kept until it is released that a state changed during.
     */
    private val ends = ConcurrentHashMap<String, StateStamp>()

    /**
     * The time a linked span that is being ended was given to end at, by span id, in milliseconds. An entry lasts from
     * [onEndTimeGiven] to the [onEnding] that follows it within the same call. It is kept because an ending span cannot
     * always say when it ends: the Kotlin implementation of OpenTelemetry's can, and the Java one's cannot until it has
     * ended, which is too late to write to it.
     */
    private val givenEndTimes = ConcurrentHashMap<String, Long>()

    fun onStart(span: ReadWriteSpan) {
        val state = stampAt(span.startTimestamp) ?: return
        if (state.sequence > 0) {
            span.setLongAttribute(StateAttributes.SEQUENCE, state.sequence)
        }
        starts[span.spanContext.spanId] = state
    }

    fun onEnding(span: ReadWriteSpan) {
        val spanId = span.spanContext.spanId
        val givenEndTimeMs = if (givenEndTimes.isEmpty()) null else givenEndTimes.remove(spanId)
        val start = (if (keepUntilReleased) starts[spanId] else starts.remove(spanId)) ?: return
        val endTimeNanos = span.endTimestamp ?: givenEndTimeMs?.millisToNanos()

        // A span that says no end time and was given none is ending now. If the stamp that held when it ended is not
        // known, the span keeps the number it started with and gets none for its end.
        val end = (if (endTimeNanos != null) stampAt(endTimeNanos) else stateProvider()) ?: return
        if (end.sequence > start.sequence) {
            span.setLongAttribute(StateAttributes.END_SEQUENCE, end.sequence)
            if (keepUntilReleased) {
                ends[spanId] = end
            }
        }
    }

    override fun startSequence(spanId: String): Long? = starts[spanId]?.sequence?.takeIf { it > 0 }

    override fun onEndTimeGiven(spanId: String, endTimeMs: Long) {
        if (starts.containsKey(spanId)) {
            givenEndTimes[spanId] = endTimeMs
        }
    }

    override fun onDiscarded(spanId: String) = release(spanId)

    /**
     * Forgets the span with the id [spanId]: it has ended and everything that exports it has seen it, or it never will end.
     */
    fun release(spanId: String) {
        starts.remove(spanId)
        ends.remove(spanId)
        givenEndTimes.remove(spanId)
    }

    /**
     * How many spans something is kept for.
     */
    fun trackedSpans(): Int = listOf<Map<String, *>>(starts, ends, givenEndTimes).flatMapTo(HashSet()) { it.keys }.size

    /**
     * The stamp that held at [timeNanos], or null if that is not known. A time equal to the time a change was recorded at
     * is after that change.
     */
    private fun stampAt(timeNanos: Long): StateStamp? {
        val current = stateProvider() ?: return null
        return if (timeNanos >= current.sinceMs.millisToNanos()) {
            current
        } else {
            stateAt(timeNanos.nanosToMillis())
        }
    }

    /**
     * [span] as an app's own exporter is handed it: with the value of every state when it started, and of each state that
     * differed when it ended, if it is linked. The values are put together when its attributes are first read, which is
     * not on the thread that ended the span.
     */
    fun withValues(span: SpanData): SpanData {
        val spanId = span.spanContext.spanId
        val start = starts[spanId] ?: return span
        return StateDecoratedSpanData(span, start, ends[spanId])
    }

    /**
     * [span] as an app's own processor is handed it when it has ended.
     */
    fun withValuesAtEnd(span: ReadableSpan): ReadableSpan {
        val spanId = span.spanContext.spanId
        val start = starts[spanId] ?: return span
        return StateDecoratedReadableSpan(span, start, ends[spanId])
    }

    /**
     * [span] as an app's own processor is handed it while it can still be written to. Writes go to the span itself.
     */
    fun withValuesWhileLive(span: ReadWriteSpan): ReadWriteSpan {
        val spanId = span.spanContext.spanId
        val start = starts[spanId] ?: return span
        return StateDecoratedReadWriteSpan(span, start, ends[spanId])
    }
}

private class StateDecoratedSpanData(
    delegate: SpanData,
    start: StateStamp,
    end: StateStamp?,
) : SpanData by delegate {
    override val attributes: Map<String, Any> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        delegate.attributes.withStateValues(start, end)
    }
}

private class StateDecoratedReadableSpan(
    private val delegate: ReadableSpan,
    private val start: StateStamp,
    private val end: StateStamp?,
) : ReadableSpan by delegate {
    override val attributes: Map<String, Any> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        delegate.attributes.withStateValues(start, end)
    }

    override fun toSpanData(): SpanData = StateDecoratedSpanData(delegate.toSpanData(), start, end)
}

private class StateDecoratedReadWriteSpan(
    private val delegate: ReadWriteSpan,
    private val start: StateStamp,
    private val end: StateStamp?,
) : ReadWriteSpan by delegate {

    // read from the span every time, as its own attributes can still change
    override val attributes: Map<String, Any>
        get() = delegate.attributes.withStateValues(start, end)

    override fun toSpanData(): SpanData = StateDecoratedSpanData(delegate.toSpanData(), start, end)
}

/**
 * These attributes with the value of every state in [start], and of each state that is not in [end] what it was in [start].
 */
internal fun Map<String, Any>.withStateValues(start: StateStamp, end: StateStamp?): Map<String, Any> {
    val startValues = start.attributes
    val endValues = end?.endAttributes(start).orEmpty()
    if (startValues.isEmpty() && endValues.isEmpty()) {
        return this
    }
    val merged = LinkedHashMap<String, Any>(size + startValues.size + endValues.size)
    merged.putAll(this)
    merged.putAll(startValues)
    merged.putAll(endValues)
    return merged
}
