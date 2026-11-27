package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.StateKeys
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * A session part's change log, rendered from its journal.
 *
 * [span] is the `emb-states` span for the part's payload. [lastSeq] is the number of the last change the log records,
 * which a span completed for a dead process takes as its end. [lastValues] are every state's values as the log last
 * records them, which a log about a process that died is stamped with.
 */
class RenderedChangeLog(
    val span: Span,
    val truncated: Boolean,
    val lastSeq: Long,
    val lastValues: List<StateValueRecord>,
)

/**
 * Renders the change log of one session part from the records of its journal, as one span that covers the same
 * stretch as the session part span, from [startTimeNanos] to [endTimeNanos]. Returns null if the journal does not
 * open with every state's values, which is the one record a change log cannot do without.
 *
 * A journal with no closing record belongs to a part whose end was not recorded, and is marked truncated. It then has
 * no counts of what a limit refused, no final values and no final numbers, and still states each limit that was
 * reached and its counts of changes made with no session part.
 */
fun renderChangeLog(
    records: List<StateJournalRecord>,
    traceId: String?,
    spanId: String?,
    startTimeNanos: Long?,
    endTimeNanos: Long?,
): RenderedChangeLog? {
    val opened = records.firstNotNullOfOrNull { it.opened } ?: return null
    val closed = records.lastOrNull()?.closed
    val attributes = mutableListOf<Attribute>()
    attributes.add(Attribute(EmbType.State.key, EmbType.State.value))
    attributes.add(Attribute(StateKeys.INITIAL_SEQ, opened.initial_seq.toString()))

    val values = LinkedHashMap<String, StateValueRecord>()
    opened.values.forEach { value ->
        values[value.name] = value
        attributes.add(Attribute(StateKeys.initialValue(value.name), value.state_value))
        if (value.system) {
            attributes.add(Attribute(StateKeys.initialValueType(value.name), StateKeys.SYSTEM))
        }
    }
    opened.not_in_session_counts.filter { it.count > 0 }.forEach {
        attributes.add(Attribute(StateKeys.notInSessionCount(it.name), it.count.toString()))
    }

    var lastSeq = opened.initial_seq
    val events = mutableListOf<SpanEvent>()
    records.forEach { record ->
        record.change?.let { change ->
            events.add(change.toEvent())
            values[change.name] = StateValueRecord(change.name, change.state_value, change.system)
            lastSeq = maxOf(lastSeq, change.seq)
        }
        record.limit?.let { limit ->
            val key = if (limit.name.isEmpty()) StateKeys.GLOBAL_CHANGE_LIMIT else StateKeys.changeLimit(limit.name)
            attributes.add(Attribute(key, limit.limit.toString()))
        }
    }

    if (closed == null) {
        attributes.add(Attribute(StateKeys.TRUNCATED, "true"))
    } else {
        if (closed.dropped_changes > 0) {
            attributes.add(Attribute(StateKeys.DROPPED_CHANGES, closed.dropped_changes.toString()))
        }
        closed.dropped_counts.filter { it.count > 0 }.forEach {
            attributes.add(Attribute(StateKeys.droppedCount(it.name), it.count.toString()))
        }
        closed.finals.forEach { final ->
            values[final.name] = StateValueRecord(final.name, final.state_value, final.system)
            attributes.add(Attribute(StateKeys.finalValue(final.name), final.state_value))
            if (final.system) {
                attributes.add(Attribute(StateKeys.finalValueType(final.name), StateKeys.SYSTEM))
            }
            attributes.add(Attribute(StateKeys.finalSeq(final.name), final.seq.toString()))
        }
    }

    val span = Span(
        traceId = traceId,
        spanId = spanId,
        parentSpanId = null,
        name = StateKeys.CHANGE_LOG_SPAN_NAME,
        startTimeNanos = startTimeNanos,
        endTimeNanos = endTimeNanos,
        status = Span.Status.UNSET,
        events = events,
        attributes = attributes,
    )
    return RenderedChangeLog(span, closed == null, lastSeq, values.values.toList())
}

private fun StateChangeRecord.toEvent(): SpanEvent {
    val attrs = ArrayList<Attribute>(attributes.size + 3)
    attrs.add(Attribute(StateKeys.SEQ, seq.toString()))
    attrs.add(Attribute(StateKeys.NEW_VALUE, state_value))
    if (system) {
        attrs.add(Attribute(StateKeys.NEW_VALUE_TYPE, StateKeys.SYSTEM))
    }
    attributes.forEach { (key, value) -> attrs.add(Attribute(key, value)) }
    return SpanEvent(
        name = StateKeys.changeEventName(name),
        timestampNanos = time_ms * NANOS_PER_MILLI,
        attributes = attrs,
    )
}
