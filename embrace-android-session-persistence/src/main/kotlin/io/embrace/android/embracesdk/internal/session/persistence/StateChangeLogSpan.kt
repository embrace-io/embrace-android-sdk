@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.StateDefinition
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv

private const val ROOT_SPAN_PARENT_ID = "0000000000000000"
private const val NANOS_PER_MILLI = 1_000_000L

/** The attributes of the session span that the change log span copies, in this order. */
private val COPIED_SESSION_ATTRIBUTES = listOf(
    EmbSessionAttributes.EMB_PROCESS_IDENTIFIER,
    EmbSessionAttributes.EMB_SESSION_PART_ID,
    EmbSessionAttributes.EMB_USER_SESSION_ID,
)

/**
 * The `emb-states` span that the session part's change log is rendered as, or null if [journal] has no opening entry. [sessionSpan]
 * is the part's session span, if it is known.
 *
 * A journal with a closing entry renders a span that ends with the session span, or an open one if the session span's own end
 * never reached the disk. One without a closing entry renders an open span marked truncated. An open span is for the
 * resurrection code to complete, as it completes any other.
 */
internal fun renderChangeLogSpan(journal: DecodedStateJournal, sessionSpan: Span?): Span? {
    val opened = journal.opened ?: return null
    val closed = journal.closed
    val changes = journal.changes.sortedBy { it.seq }.distinctBy { it.seq }
    val startTimeNanos = sessionSpan?.startTimeNanos
        ?: changes.firstOrNull()?.let { it.time_ms * NANOS_PER_MILLI }
        ?: 0L
    return Span(
        traceId = opened.trace_id,
        spanId = opened.span_id,
        parentSpanId = ROOT_SPAN_PARENT_ID,
        name = AppStateKeys.CHANGE_LOG_SPAN_NAME,
        startTimeNanos = startTimeNanos,
        endTimeNanos = closed?.let { sessionSpan?.endTimeNanos },
        status = Span.Status.UNSET,
        events = changes.map(StateJournalChange::toEvent),
        attributes = changeLogAttributes(opened, journal.limits, closed, sessionSpan),
        links = emptyList(),
    )
}

private fun changeLogAttributes(
    opened: StateJournalOpened,
    limits: List<StateJournalLimit>,
    closed: StateJournalClosed?,
    sessionSpan: Span?,
): List<Attribute> {
    val attributes = mutableListOf<Attribute>()
    attributes.add(Attribute(EmbType.State.key, EmbType.State.value))
    COPIED_SESSION_ATTRIBUTES.forEach { key ->
        sessionSpan?.attributes?.firstOrNull { it.key == key }?.let(attributes::add)
    }
    attributes.add(Attribute(AppStateKeys.INITIAL_SEQ, opened.initial_seq.toString()))
    opened.states.forEach { state ->
        attributes.add(Attribute(AppStateKeys.INITIAL_VALUE_PREFIX + state.name, state.value_))
        if (state.system) {
            attributes.add(Attribute(AppStateKeys.INITIAL_VALUE_TYPE_PREFIX + state.name, AppStateKeys.VALUE_TYPE_SYSTEM))
        }
    }
    opened.states.filter { it.not_in_session_count > 0 }.forEach { state ->
        attributes.add(Attribute(AppStateKeys.NOT_IN_SESSION_COUNT_PREFIX + state.name, state.not_in_session_count.toString()))
    }
    limits.distinctBy { it.name }.forEach { limit ->
        val key = when {
            limit.name.isEmpty() -> AppStateKeys.GLOBAL_CHANGE_LIMIT
            else -> AppStateKeys.CHANGE_LIMIT_PREFIX + limit.name
        }
        attributes.add(Attribute(key, limit.limit.toString()))
    }
    if (closed != null) {
        attributes.addAll(closingAttributes(closed))
    } else {
        attributes.add(Attribute(AppStateKeys.TRUNCATED, true.toString()))
    }
    return attributes
}

private fun closingAttributes(closed: StateJournalClosed): List<Attribute> {
    val attributes = mutableListOf<Attribute>()
    if (closed.dropped_changes > 0) {
        attributes.add(Attribute(AppStateKeys.DROPPED_CHANGES, closed.dropped_changes.toString()))
    }
    closed.states.filter { it.dropped_count > 0 }.forEach { state ->
        attributes.add(Attribute(AppStateKeys.DROPPED_COUNT_PREFIX + state.name, state.dropped_count.toString()))
    }
    closed.states.filter { it.has_final }.forEach { state ->
        attributes.add(Attribute(AppStateKeys.FINAL_VALUE_PREFIX + state.name, state.final_value))
        if (state.final_system) {
            attributes.add(Attribute(AppStateKeys.FINAL_VALUE_TYPE_PREFIX + state.name, AppStateKeys.VALUE_TYPE_SYSTEM))
        }
        attributes.add(Attribute(AppStateKeys.FINAL_SEQ_PREFIX + state.name, state.final_seq.toString()))
    }
    return attributes
}

private fun StateJournalChange.toEvent(): SpanEvent {
    val eventAttributes = mutableListOf(
        Attribute(AppStateKeys.SEQ, seq.toString()),
        Attribute(AppStateKeys.NEW_VALUE, value_),
    )
    if (system) {
        eventAttributes.add(Attribute(AppStateKeys.NEW_VALUE_TYPE, AppStateKeys.VALUE_TYPE_SYSTEM))
    }
    attributes
        .filterNot { AppStateKeys.isReserved(it.key) }
        .take(StateDefinition.MAX_CHANGE_ATTRIBUTES)
        .mapTo(eventAttributes) { Attribute(it.key, it.value_) }
    return SpanEvent(
        name = AppStateKeys.CHANGE_LOG_EVENT_PREFIX + name,
        timestampNanos = time_ms * NANOS_PER_MILLI,
        attributes = eventAttributes,
    )
}

/**
 * A state's value as listed by a change log span. [system] is whether the SDK generated the value.
 */
class ListedState(val name: String, val value: String, val system: Boolean)

/**
 * The values a change log span's list leads to, in the order the states were first seen, and [seq], the highest change number it
 * has: that of the last event, or the span's initial number when it has none.
 */
class ListedStates(val states: List<ListedState>, val seq: Long)

/**
 * The last values the `emb-states` [span] lists: the opening values with each event's new value applied in order. A state named by a
 * change limit is left out, since its later values were not listed, and a span with the limit across every state lists nothing
 * reliably, so the result is null.
 */
fun lastListedStates(span: Span): ListedStates? {
    val attributes = span.attributes.orEmpty()
    if (attributes.any { it.key == AppStateKeys.GLOBAL_CHANGE_LIMIT }) {
        return null
    }
    val values = LinkedHashMap<String, ListedState>()
    var seq = attributes.firstOrNull { it.key == AppStateKeys.INITIAL_SEQ }?.data?.toLongOrNull() ?: 0L
    attributes.forEach { attribute ->
        val key = attribute.key ?: return@forEach
        if (key.startsWith(AppStateKeys.INITIAL_VALUE_PREFIX)) {
            val name = key.removePrefix(AppStateKeys.INITIAL_VALUE_PREFIX)
            val type = attributes.firstOrNull { it.key == AppStateKeys.INITIAL_VALUE_TYPE_PREFIX + name }?.data
            values[name] = ListedState(name, attribute.data.orEmpty(), type == AppStateKeys.VALUE_TYPE_SYSTEM)
        }
    }
    span.events.orEmpty().forEach { event ->
        val name = event.name?.takeIf { it.startsWith(AppStateKeys.CHANGE_LOG_EVENT_PREFIX) }
            ?.removePrefix(AppStateKeys.CHANGE_LOG_EVENT_PREFIX) ?: return@forEach
        val eventAttributes = event.attributes.orEmpty()
        val value = eventAttributes.firstOrNull { it.key == AppStateKeys.NEW_VALUE }?.data ?: return@forEach
        val type = eventAttributes.firstOrNull { it.key == AppStateKeys.NEW_VALUE_TYPE }?.data
        values[name] = ListedState(name, value, type == AppStateKeys.VALUE_TYPE_SYSTEM)
        eventAttributes.firstOrNull { it.key == AppStateKeys.SEQ }?.data?.toLongOrNull()?.let { seq = maxOf(seq, it) }
    }
    val limited = attributes.mapNotNull { it.key }
        .filter { it.startsWith(AppStateKeys.CHANGE_LIMIT_PREFIX) }
        .mapTo(HashSet()) { it.removePrefix(AppStateKeys.CHANGE_LIMIT_PREFIX) }
    return ListedStates(values.values.filter { it.name !in limited }, seq)
}
