@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.state.ClosingState
import io.embrace.android.embracesdk.internal.state.OpeningState
import io.embrace.android.embracesdk.internal.state.StateChange
import io.embrace.android.embracesdk.internal.state.StateDefinition
import io.embrace.android.embracesdk.internal.state.StateJournalRecord
import io.embrace.android.embracesdk.internal.state.StateSnapshot
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv

internal const val JOURNAL_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
internal const val JOURNAL_USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
internal const val JOURNAL_SESSION_START_NANOS = 1726739283136000000L
internal const val JOURNAL_SESSION_END_NANOS = 1726739290000000000L

/** A value holding the characters that a naive text format would trip over, and one outside the basic multilingual plane. */
internal const val AWKWARD_VALUE = "a;b=c\nd 😀 e"

internal fun opened(
    initialSeq: Long = 0L,
    states: List<OpeningState> = emptyList(),
    sessionPartId: String = JOURNAL_PART_ID,
): StateJournalRecord = StateJournalRecord.PartOpened(sessionPartId, initialSeq, states)

internal fun openingState(name: String, value: String, system: Boolean = false, notInSession: Int = 0) =
    OpeningState(name, value, system, notInSession)

internal fun change(
    seq: Long,
    name: String,
    value: String,
    timeMs: Long = 1726739284000L + seq,
    system: Boolean = false,
    attributes: Map<String, String> = emptyMap(),
    sessionPartId: String = JOURNAL_PART_ID,
): StateJournalRecord = StateJournalRecord.Change(
    sessionPartId,
    StateChange(
        seq = seq,
        definition = StateDefinition(name),
        timeMs = timeMs,
        value = value,
        system = system,
        previousValue = null,
        previousSystem = false,
        attributes = attributes,
        after = StateSnapshot.EMPTY,
    ),
)

internal fun limitReached(state: String?, limit: Int): StateJournalRecord =
    StateJournalRecord.LimitReached(JOURNAL_PART_ID, state, limit)

internal fun closed(droppedChanges: Int = 0, states: List<ClosingState> = emptyList()): StateJournalRecord =
    StateJournalRecord.PartClosed(JOURNAL_PART_ID, droppedChanges, states)

internal fun closingState(
    name: String,
    droppedCount: Int,
    finalValue: String? = null,
    finalSystem: Boolean = false,
    finalSeq: Long = 0L,
) = ClosingState(name, droppedCount, finalValue, finalSystem, finalSeq)

/** The part's session span, as it is once the part has ended. */
internal val journalSessionSpan = Span(
    traceId = "6c9b1f2ec1d34f3c9a7d0b8e5f2a4c11",
    spanId = "aaaaaaaaaaaaaaa9",
    parentSpanId = "0000000000000000",
    name = "emb-session",
    startTimeNanos = JOURNAL_SESSION_START_NANOS,
    endTimeNanos = JOURNAL_SESSION_END_NANOS,
    status = Span.Status.OK,
    events = emptyList(),
    attributes = listOf(
        Attribute("emb.type", "ux.session"),
        Attribute(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, "pid-1"),
        Attribute(EmbSessionAttributes.EMB_SESSION_PART_ID, JOURNAL_PART_ID),
        Attribute(EmbSessionAttributes.EMB_USER_SESSION_ID, JOURNAL_USER_SESSION_ID),
    ),
    links = emptyList(),
)
