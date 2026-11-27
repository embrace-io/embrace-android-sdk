package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.state.ClosingState
import io.embrace.android.embracesdk.internal.state.OpeningState
import io.embrace.android.embracesdk.internal.state.StateJournalRecord

/**
 * The entry of the journal file that [this] record is stored as. [traceId] and [spanId] are the ids the journal is later rendered
 * with, and are only used by the record that opens a session part.
 */
internal fun StateJournalRecord.toEntry(traceId: () -> String, spanId: () -> String): StateJournalEntry = when (this) {
    is StateJournalRecord.PartOpened -> StateJournalEntry(
        opened = StateJournalOpened(
            initial_seq = initialSeq,
            states = states.map(OpeningState::toProto),
            trace_id = traceId(),
            span_id = spanId(),
        ),
    )
    is StateJournalRecord.Change -> StateJournalEntry(
        change = StateJournalChange(
            seq = change.seq,
            name = change.definition.name,
            time_ms = change.timeMs,
            value_ = change.value,
            system = change.system,
            attributes = change.attributes.map { (key, value) -> AttributeProto(key = key, value_ = value) },
        ),
    )
    is StateJournalRecord.LimitReached -> StateJournalEntry(
        limit = StateJournalLimit(name = state.orEmpty(), limit = limit),
    )
    is StateJournalRecord.PartClosed -> StateJournalEntry(
        closed = StateJournalClosed(
            dropped_changes = droppedChanges,
            states = states.map(ClosingState::toProto),
        ),
    )
}

private fun OpeningState.toProto(): StateJournalOpeningState = StateJournalOpeningState(
    name = name,
    value_ = value,
    system = system,
    not_in_session_count = notInSessionCount,
)

private fun ClosingState.toProto(): StateJournalClosingState = StateJournalClosingState(
    name = name,
    dropped_count = droppedCount,
    has_final = finalValue != null,
    final_value = finalValue.orEmpty(),
    final_system = finalSystem,
    final_seq = finalSeq,
)
