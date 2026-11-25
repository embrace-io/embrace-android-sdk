package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceObjectName
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.state.StateAttributes
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.session.persistence.StateRecord
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Returns this envelope with the session part's state [journal] added to it as one span, or unchanged if there is no journal.
 *
 * The journal is how state is persisted, not how it is delivered. This renders it in the one shape the payload has for it:
 *
 * - every state's value when the journal opened is an attribute, `emb.states.initial_value.<name>`, with
 *   `emb.states.initial_value_type.<name>` beside it for a value the SDK generated
 * - every change is an event named `emb-state-<name>`, in the order of the journal, carrying its new value and its own
 *   sequence number, `emb.states.seq`
 * - `emb.states.initial_seq` says what the initial values are as of. The changes of a journal are consecutive, so the
 *   first event is numbered one more than that and each event after it the next
 * - `emb.states.part_start_seq` says which changes were made before the session part started: those numbered up to it
 *
 * The span covers the session part: it starts and ends when the session part span does. If neither the journal nor the
 * session part span records the part's end, the process died with the part in progress, and the span is left as a
 * snapshot for resurrection to complete like any other. The session part span is given a link to it.
 *
 * A journal that does not record the end of its part is delivered as far as it goes and marked
 * `emb.states.truncated`: the process died, or the journal could not be written to, and either way the values
 * after its last event, and how many changes it did not record, are not known from it. The changes of a journal are
 * consecutive, and one read back with a change missing is delivered up to the missing change and marked the same way.
 *
 * Where [completesRunningSpans], and the part was left by a process that died, which is when neither the session part span
 * nor the journal records its end, each span the part has as a snapshot, which was running when the process died, is given
 * [StateAttributes.END_SEQUENCE]: the number of the last change the journal lists, where that is greater than the number
 * the span started with, or where the span has none because it started before the first change. A span with no end number
 * is then one no change is known to have happened during. A journal cut short still has a last recorded number, and that
 * is the one used.
 *
 * The span's ids are worked out from the session part's, so a part delivered twice delivers the same span.
 */
fun Envelope<SessionPartPayload>.withStateSpan(
    journal: List<StateRecord>,
    completesRunningSpans: Boolean = false,
): Envelope<SessionPartPayload> {
    val opening = journal.firstOrNull()?.takeIf { it.checkpoint != null } ?: return this
    val records = journal.upToFirstMissingChange()
    val partStart = records.firstOrNull { it.part_start != null }
    val partEnd = records.lastOrNull { it.part_end != null }
    val sessionSpan = (data.spans.orEmpty() + data.spanSnapshots.orEmpty())
        .singleOrNull { it.hasEmbraceAttribute(EmbType.Ux.Session) }
    val completedSpans = data.spans.orEmpty()
    val sessionEndNanos = sessionSpan?.takeIf { span -> completedSpans.any { it === span } }?.endTimeNanos
    val endNanos = sessionEndNanos ?: partEnd?.time_ms?.millisToNanos()
    val truncated = records.size < journal.size || partEnd == null
    val sessionPartId = sessionSpan?.attributes?.firstOrNull { it.key == EmbSessionAttributes.EMB_SESSION_PART_ID }
    val ids = stateSpanIds(sessionPartId?.data ?: "${opening.sequence}-${opening.time_ms}")

    val stateSpan = Span(
        traceId = ids.first,
        spanId = ids.second,
        parentSpanId = OtelIds.INVALID_SPAN_ID,
        name = SPAN_NAME.toEmbraceObjectName(),
        startTimeNanos = sessionSpan?.startTimeNanos ?: (partStart ?: opening).time_ms.millisToNanos(),
        endTimeNanos = endNanos,
        status = Span.Status.UNSET,
        events = records.mapNotNull(::changeEvent),
        attributes = sessionSpan.sharedAttributes() + stateAttributes(records, partEnd, truncated),
    )
    val link = Link(
        spanId = stateSpan.spanId,
        traceId = stateSpan.traceId,
        attributes = listOfNotNull(Attribute(LinkType.State.key, LinkType.State.value), sessionPartId),
        isRemote = false,
    )
    fun List<Span>?.linked(): List<Span>? = this?.map { span ->
        if (span === sessionSpan) span.copy(links = span.links.orEmpty() + link) else span
    }

    val lastChange = records.lastOrNull { it.change != null }?.sequence
    val spans = data.spans.linked()
    val snapshots = data.spanSnapshots.linked().let { linked ->
        if (completesRunningSpans && endNanos == null && lastChange != null) {
            linked?.map { it.withEndSequence(lastChange) }
        } else {
            linked
        }
    }
    return copy(
        data = if (endNanos != null) {
            data.copy(spans = spans.orEmpty() + stateSpan, spanSnapshots = snapshots)
        } else {
            data.copy(spans = spans, spanSnapshots = snapshots.orEmpty() + stateSpan)
        },
    )
}

/**
 * This span, which was running when its process died, with the number of the last change the process recorded as its end
 * number, if a change was made after the one it started at, and it has no end number yet.
 */
private fun Span.withEndSequence(lastChange: Long): Span {
    val present = attributes.orEmpty()
    if (present.any { it.key == StateAttributes.END_SEQUENCE }) {
        return this
    }
    val started = present.firstOrNull { it.key == StateAttributes.SEQUENCE }?.data?.toLongOrNull()
    return if (started == null || lastChange > started) {
        copy(attributes = present + Attribute(StateAttributes.END_SEQUENCE, lastChange.toString()))
    } else {
        this
    }
}

/**
 * The records of a journal up to the first change that does not have the sequence after the change before it, which is all
 * of them unless a change is missing. The journal opens with a checkpoint, whose sequence the first change follows.
 */
private fun List<StateRecord>.upToFirstMissingChange(): List<StateRecord> {
    var last = first().sequence
    forEachIndexed { index, record ->
        if (record.change != null) {
            if (record.sequence != last + 1) {
                return subList(0, index)
            }
            last = record.sequence
        }
    }
    return this
}

/**
 * The trace id and span id of the state span of the session part [seed] identifies.
 */
private fun stateSpanIds(seed: String): Pair<String, String> {
    val digest = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest("$SPAN_NAME:$seed".toByteArray()))
    return digest.nextHex() + digest.nextHex() to digest.nextHex()
}

/**
 * The next eight bytes as sixteen hexadecimal digits.
 */
private fun ByteBuffer.nextHex(): String = long.toULong().toString(HEX_RADIX).padStart(HEX_DIGITS_OF_A_LONG, '0')

/**
 * The attributes every span of a session part carries, taken from its session part span.
 */
private fun Span?.sharedAttributes(): List<Attribute> = buildList {
    add(Attribute(EmbType.State.key, EmbType.State.value))
    SESSION_ATTRIBUTES.forEach { key ->
        this@sharedAttributes?.attributes?.firstOrNull { it.key == key }?.let(::add)
    }
}

/**
 * What the journal's [records] say about the states as a whole: the values it opened with, what those values are as of,
 * which changes came before the session part started, how many changes it does not hold, and whether it stops short.
 *
 * Two counts are kept apart. Changes folded into the opening values were made before those values, so the events still
 * lead on from them. Changes dropped during the part leave the events short of the values the part ended with, which are
 * then given as well.
 */
private fun stateAttributes(
    records: List<StateRecord>,
    partEnd: StateRecord?,
    truncated: Boolean,
): List<Attribute> = buildList {
    val opening = records.first()
    val partStart = records.firstOrNull { it.part_start != null }
    opening.checkpoint?.values?.forEach { value ->
        add(Attribute("${StateAttributes.INITIAL_VALUE_PREFIX}${value.state}", value.value_))
        if (value.system) {
            add(Attribute("${StateAttributes.INITIAL_VALUE_TYPE_PREFIX}${value.state}", SYSTEM_VALUE_TYPE))
        }
    }
    add(Attribute(StateAttributes.INITIAL_SEQUENCE, opening.sequence.toString()))
    // a journal that stops before its part start holds only changes made before the part
    val partStartSequence = partStart?.sequence ?: records.last { it.change != null || it === opening }.sequence
    add(Attribute(StateAttributes.PART_START_SEQUENCE, partStartSequence.toString()))
    partStart?.part_start?.dropped_changes?.takeIf { it > 0 }?.let { folded ->
        add(Attribute(StateAttributes.FOLDED_CHANGES, folded.toString()))
    }
    partEnd?.part_end?.let { end ->
        if (end.dropped_changes > 0) {
            add(Attribute(StateAttributes.DROPPED_CHANGES, end.dropped_changes.toString()))
        }
        end.values.forEach { value ->
            add(Attribute("${StateAttributes.FINAL_VALUE_PREFIX}${value.state}", value.value_))
            if (value.system) {
                add(Attribute("${StateAttributes.FINAL_VALUE_TYPE_PREFIX}${value.state}", SYSTEM_VALUE_TYPE))
            }
        }
    }
    if (truncated) {
        add(Attribute(StateAttributes.TRUNCATED, true.toString()))
    }
}

/**
 * The event a change is delivered as, or null if [record] is not a change.
 */
private fun changeEvent(record: StateRecord): SpanEvent? {
    val change = record.change ?: return null
    val value = change.value_ ?: return null
    return SpanEvent(
        name = "$EVENT_NAME_PREFIX${value.state}".toEmbraceObjectName(),
        timestampNanos = record.time_ms.millisToNanos(),
        attributes = buildList {
            change.attributes.forEach { (key, attribute) -> add(Attribute(key, attribute)) }
            add(Attribute(StateAttributes.SEQUENCE, record.sequence.toString()))
            add(Attribute(StateAttributes.NEW_VALUE, value.value_))
            if (value.system) {
                add(Attribute(StateAttributes.NEW_VALUE_TYPE, SYSTEM_VALUE_TYPE))
            }
        },
    )
}

/**
 * The name of the span a state journal is delivered as, before the prefix every span the SDK records for itself gets.
 */
internal const val SPAN_NAME: String = "states"

private const val EVENT_NAME_PREFIX = "state-"
private const val SYSTEM_VALUE_TYPE = StateAttributes.SYSTEM
private const val HEX_RADIX = 16
private const val HEX_DIGITS_OF_A_LONG = 16

private val SESSION_ATTRIBUTES = listOf(
    EmbSessionAttributes.EMB_PROCESS_IDENTIFIER,
    EmbSessionAttributes.EMB_SESSION_PART_ID,
    EmbSessionAttributes.EMB_USER_SESSION_ID,
)
