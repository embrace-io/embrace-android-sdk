package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class StateChangeLogSpanTest {

    private companion object {
        private const val TRACE_ID = "0123456789abcdef0123456789abcdef"
        private const val SPAN_ID = "0123456789abcdef"
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val SESSION_PART_ID_KEY = "emb.session_part_id"

        private val opened = StateJournalOpened(
            initial_seq = 7,
            states = listOf(
                StateJournalOpeningState(name = "net", value_ = "wifi"),
                StateJournalOpeningState(name = "power", value_ = "on", system = true, not_in_session_count = 3),
                StateJournalOpeningState(name = "screen", value_ = AWKWARD_VALUE, not_in_session_count = 0),
            ),
            trace_id = TRACE_ID,
            span_id = SPAN_ID,
        )

        private val limits = listOf(
            StateJournalLimit(name = "net", limit = 2),
            StateJournalLimit(name = "", limit = 50),
        )

        private val closing = StateJournalClosed(
            dropped_changes = 4,
            states = listOf(
                StateJournalClosingState(
                    name = "net",
                    dropped_count = 3,
                    has_final = true,
                    final_value = "5g",
                    final_system = true,
                    final_seq = 12,
                ),
                StateJournalClosingState(name = "power", dropped_count = 0, has_final = false),
                StateJournalClosingState(
                    name = "screen",
                    dropped_count = 1,
                    has_final = true,
                    final_value = AWKWARD_VALUE,
                    final_seq = 15,
                ),
            ),
        )

        private fun changeEntry(
            seq: Long,
            name: String,
            value: String,
            system: Boolean = false,
            attrs: List<AttributeProto> = emptyList(),
        ) = StateJournalChange(seq = seq, name = name, time_ms = 1000L + seq, value_ = value, system = system, attributes = attrs)

        private fun journal(
            changes: List<StateJournalChange> = emptyList(),
            limits: List<StateJournalLimit> = emptyList(),
            closed: StateJournalClosed? = null,
            opened: StateJournalOpened? = Companion.opened,
        ) = DecodedStateJournal(opened, changes, limits, closed, lastWrittenMs = 0L)

        private fun Span.attributePairs() = attributes?.map { it.key to it.data }
    }

    @Test
    fun `no opening entry renders nothing`() {
        assertNull(renderChangeLogSpan(journal(opened = null), journalSessionSpan))
    }

    @Test
    fun `a closed journal renders every attribute and event in order with exact values`() {
        val ownAttrs = listOf(
            AttributeProto("emb.app.state.sneaky", "x"),
            AttributeProto("emb.state.old", "x"),
            AttributeProto("a", "1"),
            AttributeProto("b", "2"),
            AttributeProto("c", "3"),
            AttributeProto("d", "4"),
            AttributeProto("e", "5"),
            AttributeProto("f", "6"),
            AttributeProto("g", "7"),
            AttributeProto("h", "8"),
        )
        val changes = listOf(
            changeEntry(9, "net", "4g", attrs = ownAttrs),
            changeEntry(8, "power", "off", system = true),
            changeEntry(8, "power", "duplicate of a number already taken"),
        )
        val span = checkNotNull(renderChangeLogSpan(journal(changes, limits, closing), journalSessionSpan))

        assertEquals("emb-states", span.name)
        assertEquals(TRACE_ID, span.traceId)
        assertEquals(SPAN_ID, span.spanId)
        assertEquals("0000000000000000", span.parentSpanId)
        assertEquals(JOURNAL_SESSION_START_NANOS, span.startTimeNanos)
        assertEquals(JOURNAL_SESSION_END_NANOS, span.endTimeNanos)
        assertEquals(Span.Status.UNSET, span.status)
        assertEquals(emptyList<Any>(), span.links)
        assertEquals(
            listOf(
                "emb.type" to "state",
                "emb.process_identifier" to "pid-1",
                SESSION_PART_ID_KEY to JOURNAL_PART_ID,
                "emb.user_session_id" to JOURNAL_USER_SESSION_ID,
                "emb.app.state.initial_seq" to "7",
                "emb.app.state.initial_value.net" to "wifi",
                "emb.app.state.initial_value.power" to "on",
                "emb.app.state.initial_value_type.power" to "system",
                "emb.app.state.initial_value.screen" to AWKWARD_VALUE,
                "emb.app.state.not_in_session_count.power" to "3",
                "emb.app.state.change_limit.net" to "2",
                "emb.app.state.global_change_limit" to "50",
                "emb.app.state.dropped_changes" to "4",
                "emb.app.state.dropped_count.net" to "3",
                "emb.app.state.dropped_count.screen" to "1",
                "emb.app.state.final_value.net" to "5g",
                "emb.app.state.final_value_type.net" to "system",
                "emb.app.state.final_seq.net" to "12",
                "emb.app.state.final_value.screen" to AWKWARD_VALUE,
                "emb.app.state.final_seq.screen" to "15",
            ),
            span.attributePairs(),
        )

        val events = checkNotNull(span.events)
        assertEquals(listOf("emb-state-power", "emb-state-net"), events.map { it.name })
        assertEquals(listOf(1008L * NANOS_PER_MILLI, 1009L * NANOS_PER_MILLI), events.map { it.timestampNanos })
        assertEquals(
            listOf(
                "emb.app.state.seq" to "8",
                "emb.app.state.new_value" to "off",
                "emb.app.state.new_value_type" to "system",
            ),
            events[0].attributes?.map { it.key to it.data },
        )
        assertEquals(
            listOf(
                "emb.app.state.seq" to "9",
                "emb.app.state.new_value" to "4g",
                "a" to "1",
                "b" to "2",
                "c" to "3",
                "d" to "4",
                "e" to "5",
                "f" to "6",
                "g" to "7",
            ),
            events[1].attributes?.map { it.key to it.data },
        )
    }

    @Test
    fun `a journal with no closing entry renders open and truncated with its limits stated`() {
        val span = checkNotNull(renderChangeLogSpan(journal(limits = limits), journalSessionSpan))

        assertNull(span.endTimeNanos)
        assertEquals(JOURNAL_SESSION_START_NANOS, span.startTimeNanos)
        assertEquals(Span.Status.UNSET, span.status)
        assertEquals(
            listOf(
                "emb.type" to "state",
                "emb.process_identifier" to "pid-1",
                SESSION_PART_ID_KEY to JOURNAL_PART_ID,
                "emb.user_session_id" to JOURNAL_USER_SESSION_ID,
                "emb.app.state.initial_seq" to "7",
                "emb.app.state.initial_value.net" to "wifi",
                "emb.app.state.initial_value.power" to "on",
                "emb.app.state.initial_value_type.power" to "system",
                "emb.app.state.initial_value.screen" to AWKWARD_VALUE,
                "emb.app.state.not_in_session_count.power" to "3",
                "emb.app.state.change_limit.net" to "2",
                "emb.app.state.global_change_limit" to "50",
                "emb.app.state.truncated" to "true",
            ),
            span.attributePairs(),
        )
    }

    @Test
    fun `with no session span the span starts at the first change and copies no ids`() {
        val span =
            checkNotNull(renderChangeLogSpan(journal(listOf(changeEntry(9, "net", "4g"), changeEntry(8, "net", "3g"))), null))
        assertEquals(1008L * NANOS_PER_MILLI, span.startTimeNanos)
        assertEquals("emb.type", span.attributes?.first()?.key)
        assertEquals(null, span.attributes?.firstOrNull { it.key == SESSION_PART_ID_KEY })

        val empty = checkNotNull(renderChangeLogSpan(journal(), null))
        assertEquals(0L, empty.startTimeNanos)
    }

    @Test
    fun `a closed journal whose session span has no end is left open, and is not marked as cut short`() {
        val open = journalSessionSpan.copy(endTimeNanos = null)
        val span = checkNotNull(renderChangeLogSpan(journal(closed = StateJournalClosed()), open))
        assertNull(span.endTimeNanos)
        val keys = span.attributes?.mapNotNull { it.key }.orEmpty()
        assertEquals(emptyList<String>(), keys.filter { it.contains("dropped") || it.contains("truncated") })
    }

    @Test
    fun `the last listed states are the opening values with each change applied`() {
        val changes = listOf(
            changeEntry(8, "net", "4g"),
            changeEntry(9, "newstate", "x", system = true),
            changeEntry(10, "power", "off"),
        )
        val span = checkNotNull(renderChangeLogSpan(journal(changes), journalSessionSpan))

        val listed = checkNotNull(lastListedStates(span))
        assertEquals(10L, listed.seq)
        assertEquals(listOf("net", "power", "screen", "newstate"), listed.states.map { it.name })
        assertEquals(listOf("4g", "off", AWKWARD_VALUE, "x"), listed.states.map { it.value })
        assertEquals(listOf(false, false, false, true), listed.states.map { it.system })
    }

    @Test
    fun `with no events the last listed states are the opening ones at the initial number`() {
        val listed = checkNotNull(lastListedStates(checkNotNull(renderChangeLogSpan(journal(), null))))
        assertEquals(7L, listed.seq)
        assertEquals(listOf("wifi", "on", AWKWARD_VALUE), listed.states.map { it.value })
        assertEquals(listOf(false, true, false), listed.states.map { it.system })
    }

    @Test
    fun `a state past its limit is left out of the last listed states`() {
        val changes = listOf(changeEntry(8, "net", "4g"), changeEntry(9, "power", "off"))
        val span = checkNotNull(renderChangeLogSpan(journal(changes, listOf(StateJournalLimit("net", 1))), null))

        val listed = checkNotNull(lastListedStates(span))
        assertEquals(listOf("power", "screen"), listed.states.map { it.name })
        assertEquals(9L, listed.seq)
    }

    @Test
    fun `the global limit gives no last listed states`() {
        val span = checkNotNull(renderChangeLogSpan(journal(limits = listOf(StateJournalLimit("", 5))), null))
        assertNull(lastListedStates(span))
    }
}
