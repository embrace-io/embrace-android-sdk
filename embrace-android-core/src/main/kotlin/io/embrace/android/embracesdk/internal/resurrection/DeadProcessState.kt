package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.internal.arch.schema.StateKeys
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
import io.embrace.android.embracesdk.internal.session.persistence.DecodedStateJournal
import io.embrace.android.embracesdk.internal.session.persistence.RenderedChangeLog
import io.embrace.android.embracesdk.internal.session.persistence.renderChangeLog
import io.embrace.android.embracesdk.internal.state.StateSnapshot
import java.util.UUID

/**
 * What a dead process last recorded of the state, read from the journal of its part: the number of its last change
 * and every state's value as it stood then, packed as a log is stamped.
 */
internal class DeadProcessState(val lastSeq: Long, val packed: String) {

    /**
     * The stamp a log about the dead process carries, such as its native crash.
     */
    fun stamp(): Map<String, String> = buildMap {
        if (packed.isNotEmpty()) {
            put(StateKeys.PACKED, packed)
        }
        if (lastSeq > 0) {
            put(StateKeys.SEQ, lastSeq.toString())
        }
    }
}

/**
 * Reads what the dead process last recorded from the journal of its part, or null if the part has no journal.
 */
internal fun DecodedStateJournal.deadProcessState(): DeadProcessState? {
    val rendered = renderChangeLog(records, null, null, null, null) ?: return null
    var snapshot = StateSnapshot.EMPTY
    rendered.lastValues.forEach {
        snapshot = snapshot.with(rendered.lastSeq, 0, it.name, it.state_value, it.system)
    }
    // a closed log's final values can be past its last listed change, so the stamp carries the number they are as of
    val finalSeq = records.lastOrNull()?.closed?.finals?.maxOfOrNull { it.seq } ?: 0L
    return DeadProcessState(maxOf(rendered.lastSeq, finalSeq), if (snapshot.size > 0) snapshot.packed else "")
}

/**
 * Gives each span the dead process left running the last number its part's change log records, as the end the next
 * launch completes it with, unless no state is known to have changed while it ran.
 */
internal fun Envelope<SessionPartPayload>.withStateEndOnSnapshots(lastSeq: Long): Envelope<SessionPartPayload> {
    val snapshots = data.spanSnapshots ?: return this
    return copy(
        data = data.copy(
            spanSnapshots = snapshots.map { span ->
                val attributes = span.attributes.orEmpty()
                val startSeq = attributes.firstOrNull { it.key == StateKeys.SEQ }?.data?.toLongOrNull() ?: 0L
                // a span that started at or after the last listed change, as one can after changes a limit refused, is
                // not known to have seen a change, and never takes an end below its start
                if (startSeq >= lastSeq || attributes.any { it.key == StateKeys.END_SEQ }) {
                    span
                } else {
                    span.copy(attributes = attributes + Attribute(StateKeys.END_SEQ, lastSeq.toString()))
                }
            },
        ),
    )
}

/**
 * Adds the part's change log, rendered from its journal, to its spans, covering the same stretch as its session part
 * span. The envelope is returned as it is if it has no journal or no session part span to place one against.
 */
internal fun Envelope<SessionPartPayload>.withStateChangeLog(
    journal: DecodedStateJournal?,
): Envelope<SessionPartPayload> {
    val records = journal?.records ?: return this
    val sessionSpan = getSessionPartSpan() ?: return this
    val rendered: RenderedChangeLog = renderChangeLog(
        records = records,
        traceId = sessionSpan.traceId,
        spanId = changeLogSpanId(sessionSpan),
        startTimeNanos = sessionSpan.startTimeNanos,
        endTimeNanos = sessionSpan.endTimeNanos,
    ) ?: return this
    return copy(data = data.copy(spans = data.spans.orEmpty() + rendered.span))
}

/**
 * A span ID for the change log that is the same however often the part is read back, derived from its session span.
 */
private fun changeLogSpanId(sessionSpan: Span): String {
    val seed = "${sessionSpan.spanId}:${StateKeys.CHANGE_LOG_SPAN_NAME}"
    return "%016x".format(UUID.nameUUIDFromBytes(seed.toByteArray()).mostSignificantBits)
}
