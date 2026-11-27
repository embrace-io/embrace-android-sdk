package io.embrace.android.embracesdk.internal.session.persistence

import okio.BufferedSource
import java.io.IOException

/**
 * What a state journal file holds. [opened] and [closed] are null when the file has no such entry, and [lastWrittenMs] is the latest
 * time a write to the file recorded, or 0 if none did. [corruption] is the failure to read a frame or an entry that was all present,
 * which stopped the read: a file that merely ends mid-record is not corrupt.
 */
internal class DecodedStateJournal(
    val opened: StateJournalOpened?,
    val changes: List<StateJournalChange>,
    val limits: List<StateJournalLimit>,
    val closed: StateJournalClosed?,
    val lastWrittenMs: Long,
    val corruption: Throwable? = null,
)

/**
 * Decodes the state journal held in [source], frame by frame. Reading stops at the first frame that cannot be read and keeps every
 * entry before it, so a process that died mid-append loses only the entry it was writing. The first `opened` and the first `closed`
 * entry are the ones kept.
 *
 * Throws [IOException] if the file was written in a format other than [FORMAT_VERSION], in which case none of it is read.
 */
internal fun readStateJournal(
    source: BufferedSource,
    maxBytes: Long = MAX_PART_FILE_BYTES,
    maxRecordBytes: Long = MAX_RECORD_BYTES,
): DecodedStateJournal = StateJournalDecoder(SpanCollectionReader(source, maxBytes, maxRecordBytes)).read()

private class StateJournalDecoder(private val collection: SpanCollectionReader) {

    private var versioned = false
    private var corruption: Throwable? = null
    private var lastWrittenMs = 0L
    private var opened: StateJournalOpened? = null
    private var closed: StateJournalClosed? = null
    private val changes = mutableListOf<StateJournalChange>()
    private val limits = mutableListOf<StateJournalLimit>()

    fun read(): DecodedStateJournal {
        var reading = true
        while (reading) {
            reading = readFrame()
        }
        if (!versioned) {
            return DecodedStateJournal(null, emptyList(), emptyList(), null, 0L, collection.corruption)
        }
        return DecodedStateJournal(opened, changes, limits, closed, lastWrittenMs, corruption ?: collection.corruption)
    }

    private fun readFrame(): Boolean {
        val tag = collection.nextTag() ?: return false
        return when (tag) {
            SPAN_COLLECTION_VERSION_TAG -> readVersion()
            SPAN_COLLECTION_RECORD_TAG -> readEntry()
            SPAN_COLLECTION_WRITTEN_AT_TAG -> readWrittenAt()
            else -> collection.skipFrame()
        }
    }

    private fun readVersion(): Boolean {
        val version = collection.readVarint32() ?: return false
        if (version != FORMAT_VERSION) {
            throw IOException(UNSUPPORTED_VERSION_MSG)
        }
        versioned = true
        return true
    }

    private fun readWrittenAt(): Boolean {
        val writtenAtMs = collection.readVarint64() ?: return false
        if (versioned) {
            lastWrittenMs = maxOf(lastWrittenMs, writtenAtMs)
        }
        return true
    }

    private fun readEntry(): Boolean {
        val record = collection.readRecord() ?: return false
        if (!versioned) {
            return true
        }
        val entry = try {
            StateJournalEntry.ADAPTER.decode(record)
        } catch (exc: Exception) {
            // what is kept has to be the start of the truth with no hole in it, so nothing after this entry is read
            corruption = corruption ?: exc
            return false
        }
        entry.opened?.let { if (opened == null) opened = it }
        entry.change?.let(changes::add)
        entry.limit?.let(limits::add)
        entry.closed?.let { if (closed == null) closed = it }
        return true
    }
}
