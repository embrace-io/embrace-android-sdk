package io.embrace.android.embracesdk.internal.session.persistence

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class StateJournalReaderTest {

    private companion object {
        /** Field 9 as a varint, which no version of the journal has held. */
        private val UNKNOWN_FIELD = byteArrayOf(0x48, 0x01)

        /** An intact frame round a record body holding an invalid field encoding. */
        private val UNDECODABLE_RECORD = byteArrayOf(0x12, 0x01, 0x0E)

        private fun decode(bytes: ByteArray, maxBytes: Long = MAX_PART_FILE_BYTES): DecodedStateJournal =
            Buffer().write(bytes).use { readStateJournal(it, maxBytes) }

        private fun sequences(journal: DecodedStateJournal) = journal.records.map { it.sequence }
    }

    private val first = stateRecord(1)
    private val second = stateRecord(2)
    private val third = stateRecord(3)

    @Test
    fun `every complete record is returned in order with the last write time`() {
        val bytes = stateJournalHeader() + stateJournalAppend(listOf(first, second), 100L) +
            stateJournalAppend(listOf(third), 200L)
        val decoded = decode(bytes)
        assertEquals(listOf(first, second, third), decoded.records)
        assertEquals(200L, decoded.lastWrittenMs)
        assertNull(decoded.corruption)
    }

    @Test
    fun `a journal holding only the header reads back no records`() {
        val decoded = decode(stateJournalHeader())
        assertEquals(emptyList<StateRecord>(), decoded.records)
        assertNull(decoded.lastWrittenMs)
        assertNull(decoded.corruption)
    }

    @Test
    fun `a record cut short by a process that died is not an error`() {
        val whole = stateJournalLog(listOf(first, second), 100L)
        val tornBytes = stateJournalLog(listOf(first), 100L) + stateJournalAppend(listOf(second)).copyOf(5)
        assertEquals(listOf(first), decode(tornBytes).records)
        assertNull(decode(tornBytes).corruption)

        for (cut in JOURNAL_HEADER_SIZE until whole.size) {
            val decoded = decode(whole.copyOf(cut))
            assertNull("cut at $cut", decoded.corruption)
            assertEquals(decoded.records, listOf(first, second).take(decoded.records.size))
        }
    }

    @Test
    fun `reading stops at a record that does not decode and reports it`() {
        val bytes = stateJournalHeader() + stateJournalAppend(listOf(first)) + UNDECODABLE_RECORD +
            stateJournalAppend(listOf(third))
        val decoded = decode(bytes)
        assertEquals(listOf(1L), sequences(decoded))
        assertNotNull(decoded.corruption)
    }

    @Test
    fun `a journal of another version reads back no records and is reported`() {
        val decoded = decode(stateJournalHeader(FORMAT_VERSION + 1) + stateJournalAppend(listOf(first)))
        assertEquals(emptyList<StateRecord>(), decoded.records)
        assertEquals(UNSUPPORTED_VERSION_MSG, decoded.corruption?.message)
    }

    @Test
    fun `a journal with no header is reported`() {
        val decoded = decode(stateJournalAppend(listOf(first), 100L))
        assertEquals(emptyList<StateRecord>(), decoded.records)
        assertNull(decoded.lastWrittenMs)
        assertEquals(UNSUPPORTED_VERSION_MSG, decoded.corruption?.message)
    }

    @Test
    fun `an empty journal is reported`() {
        val decoded = decode(byteArrayOf())
        assertEquals(emptyList<StateRecord>(), decoded.records)
        assertEquals(UNSUPPORTED_VERSION_MSG, decoded.corruption?.message)
    }

    @Test
    fun `unknown tags are skipped`() {
        val bytes = stateJournalHeader() + UNKNOWN_FIELD + stateJournalAppend(listOf(first, second))
        val decoded = decode(bytes)
        assertEquals(listOf(first, second), decoded.records)
        assertNull(decoded.corruption)
    }

    @Test
    fun `reading stops at the record that passes the size limit`() {
        val bytes = stateJournalLog(listOf(first, second, third))
        val limit = StateRecord.ADAPTER.encode(first).size + StateRecord.ADAPTER.encode(second).size.toLong()
        assertEquals(listOf(1L, 2L), sequences(decode(bytes, limit)))
    }
}
