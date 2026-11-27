package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.arch.schema.StateKeys
import io.embrace.android.embracesdk.internal.payload.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class StateJournalTest {

    private val opened = StateJournalRecord(
        opened = StatePartOpened(
            initial_seq = 40,
            values = listOf(StateValueRecord("network", "wifi", false)),
            not_in_session_counts = listOf(StateCountRecord("network", 2)),
        ),
    )
    private val change = StateJournalRecord(
        change = StateChangeRecord(seq = 41, name = "network", state_value = "none", time_ms = 5),
    )
    private val limit = StateJournalRecord(limit = StateLimitReached("network", 100))
    private val closed = StateJournalRecord(
        closed = StatePartClosed(
            dropped_changes = 1,
            dropped_counts = listOf(StateCountRecord("network", 1)),
            finals = listOf(StateFinalRecord("network", "wan", false, 42)),
        ),
    )

    @Test
    fun `a journal appended a record at a time reads back whole`() {
        val bytes = encode(listOf(opened)) + encode(listOf(change, limit)) + encode(listOf(closed))
        val journal = checkNotNull(readStateJournal(bytes))
        assertEquals(listOf(opened, change, limit, closed), journal.records)
        assertNull(journal.corruption)
    }

    @Test
    fun `a record cut short by a process dying loses only that record`() {
        val whole = encode(listOf(opened)) + encode(listOf(change))
        val cut = whole + encode(listOf(closed)).copyOf(5)
        val journal = checkNotNull(readStateJournal(cut))
        assertEquals(listOf(opened, change), journal.records)
        assertNotNull(journal.corruption)
    }

    @Test
    fun `a journal with no closing record renders a truncated change log that still states the limit`() {
        val rendered = checkNotNull(renderChangeLog(listOf(opened, change, limit), "t", "s", 1, 2))
        val attrs = rendered.span.attributes.orEmpty().associate { it.key to it.data }
        assertEquals("true", attrs[StateKeys.TRUNCATED])
        assertEquals("40", attrs[StateKeys.INITIAL_SEQ])
        assertEquals("wifi", attrs[StateKeys.initialValue("network")])
        assertEquals("2", attrs[StateKeys.notInSessionCount("network")])
        assertEquals("100", attrs[StateKeys.changeLimit("network")])
        assertNull(attrs[StateKeys.DROPPED_CHANGES])
        assertEquals(41, rendered.lastSeq)
        assertEquals("none", rendered.lastValues.single().state_value)
        val event = rendered.span.events.orEmpty().single()
        assertEquals("emb-state-network", event.name)
        assertEquals(5_000_000L, event.timestampNanos)
    }

    @Test
    fun `a closed journal renders its counts and final values`() {
        val rendered = checkNotNull(renderChangeLog(listOf(opened, change, limit, closed), "t", "s", 1, 2))
        val span: Span = rendered.span
        val attrs = span.attributes.orEmpty().associate { it.key to it.data }
        assertNull(attrs[StateKeys.TRUNCATED])
        assertEquals("1", attrs[StateKeys.DROPPED_CHANGES])
        assertEquals("1", attrs[StateKeys.droppedCount("network")])
        assertEquals("wan", attrs[StateKeys.finalValue("network")])
        assertEquals("42", attrs[StateKeys.finalSeq("network")])
        assertEquals("wan", rendered.lastValues.single().state_value)
    }

    private fun encode(records: List<StateJournalRecord>, first: Boolean = records.firstOrNull()?.opened != null): ByteArray =
        StateJournal.ADAPTER.encode(StateJournal(format_version = if (first) FORMAT_VERSION else 0, records = records))
}
