package io.embrace.android.embracesdk.internal.session.persistence

import okio.BufferedSource
import java.io.IOException

/**
 * Decodes the append-only state journal held in [source].
 *
 * A process can die part way through an append, so a journal that stops mid-record is expected and
 * is not reported: every record written in full before it is kept. A record that is all present but
 * does not decode is corruption, as is a frame that cannot be followed at all; both are reported
 * alongside the records read back before them. Reading stops at the first of either, as the records
 * of a journal lead on from one another and those after a missing one cannot be replayed. A journal
 * that opens with no version record, or one for another layout, reads back no records and is reported.
 */
internal fun readStateJournal(
    source: BufferedSource,
    maxBytes: Long = MAX_PART_FILE_BYTES,
    maxRecordBytes: Long = MAX_RECORD_BYTES,
): DecodedStateJournal = StateJournalDecoder(SpanCollectionReader(source, maxBytes, maxRecordBytes)).read()

private class StateJournalDecoder(private val collection: SpanCollectionReader) {

    private val records = mutableListOf<StateRecord>()
    private var corruption: IOException? = null
    private var versioned = false
    private var lastWrittenMs = 0L

    fun read(): DecodedStateJournal {
        while (readFrame()) {
            // each frame is applied as it is read
        }
        if (!versioned) {
            return DecodedStateJournal(emptyList(), null, corruption ?: collection.corruption ?: unsupportedVersion())
        }
        return DecodedStateJournal(records, lastWrittenMs.takeIf { it > 0 }, corruption ?: collection.corruption)
    }

    private fun readFrame(): Boolean {
        val tag = collection.nextTag() ?: return false
        return when (tag) {
            SPAN_COLLECTION_VERSION_TAG -> readVersion()
            SPAN_COLLECTION_RECORD_TAG -> readRecord()
            SPAN_COLLECTION_WRITTEN_AT_TAG -> readWrittenAt()
            else -> collection.skipFrame()
        }
    }

    private fun readVersion(): Boolean {
        val version = collection.readVarint32() ?: return false
        if (version != FORMAT_VERSION) {
            corruption = unsupportedVersion()
            return false
        }
        versioned = true
        records.clear()
        return true
    }

    private fun readRecord(): Boolean {
        val bytes = collection.readRecord() ?: return false
        return try {
            records.add(StateRecord.ADAPTER.decode(bytes))
            true
        } catch (exc: Exception) {
            corruption = corruption ?: IOException("State record did not decode", exc)
            false
        }
    }

    private fun readWrittenAt(): Boolean {
        val writtenAtMs = collection.readVarint64() ?: return false
        lastWrittenMs = maxOf(lastWrittenMs, writtenAtMs)
        return true
    }

    private fun unsupportedVersion() = IOException(UNSUPPORTED_VERSION_MSG)
}
