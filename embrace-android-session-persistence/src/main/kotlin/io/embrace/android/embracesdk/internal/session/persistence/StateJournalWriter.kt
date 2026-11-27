package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.io.File
import java.io.IOException

/**
 * Appends state records to the journal a session part directory holds.
 *
 * The journal is only ever added to, so the cost of a write is proportional to the records being
 * written rather than to everything journaled for the session part so far. Each record is self
 * contained, which lets [readStateJournal] recover every record journaled before a process died
 * mid-append. Each append ends with the time it was written.
 *
 * What the journal holds is always the start of what it was given, with nothing missing from the
 * middle: the first record that cannot be journaled, because the journal is full, the record is too
 * large or a write failed, is the last thing the journal of that session part is offered. Every
 * record after it is refused, even one that would have fit.
 *
 * The records are stored as given: the writer does not interpret them.
 */
class StateJournalWriter(
    private val target: SessionPartWriteTarget,
    private val errorHandler: ErrorHandler,
    private val maxBytes: Long = MAX_PART_FILE_BYTES,
    private val maxRecordBytes: Long = MAX_RECORD_BYTES,
) {

    private var file: SpanCollectionFile? = null

    /**
     * The session part whose journal was last written to.
     */
    private var journaled: SessionPartDirectory? = null

    /**
     * Whether a record could not be added to the journal of [journaled], after which nothing more is.
     */
    private var stopped = false

    /**
     * Appends [records] to the journal for the active session part, leaving the records already
     * journaled in place, and returns whether every one of them was journaled. A session part with
     * no state changes has no journal at all, which reconstruction reads back as no records.
     */
    fun write(records: List<StateRecord>): Boolean = SystemTrace.trace("mf-write-state-journal") {
        try {
            writeImpl(records)
        } catch (exc: Throwable) {
            // the write may have landed in part. nothing is appended after it, so the reader finds
            // at worst a journal that stops mid-record, as one left by a process that died does
            stopped = true
            closeFile()
            target.reportWriteFailure(exc, ::trackFailure)
            false
        }
    }

    /**
     * Releases the file held open for the session part written to so far.
     */
    fun close() {
        closeFile()
    }

    private fun writeImpl(records: List<StateRecord>): Boolean {
        if (records.isEmpty()) {
            return true
        }
        val directory = target.directory ?: return false
        if (directory != journaled) {
            // another session part, with a journal of its own
            closeFile()
            journaled = directory
            stopped = false
        }
        if (stopped) {
            return false
        }
        val file = openFile(directory) ?: return stop()
        val header = if (file.size == 0L) JOURNAL_HEADER else ByteArray(0)
        val writtenAt = target.currentTimeMs()
        val budget = maxBytes - file.size - header.size - writtenAtSize(writtenAt)
        val fitting = recordsThatFit(records, budget)
        if (fitting.isNotEmpty()) {
            val appended = StateJournal(records = fitting, written_at_ms = writtenAt)
            file.append(header + StateJournal.ADAPTER.encode(appended))
        }
        if (fitting.size < records.size) {
            trackFailure(IOException(DROPPED_STATE_RECORD_MSG))
            return stop()
        }
        return true
    }

    /**
     * The records from the start of [records] that the journal can hold: those before the first that
     * would take it past [budget] or that is larger than [maxRecordBytes], which the reader stops at.
     */
    private fun recordsThatFit(records: List<StateRecord>, budget: Long): List<StateRecord> {
        var remaining = budget
        return records.takeWhile { record ->
            val size = StateJournal.ADAPTER.encodedSize(StateJournal(records = listOf(record))).toLong()
            val fits = size <= remaining && size <= maxRecordBytes
            if (fits) {
                remaining -= size
            }
            fits
        }
    }

    private fun stop(): Boolean {
        stopped = true
        return false
    }

    /**
     * The file to append to, or null if [directory] is not on disk.
     */
    private fun openFile(directory: SessionPartDirectory): SpanCollectionFile? {
        file?.let { return it }
        val partDir = target.partDir(directory, ::trackFailure) ?: return null
        return SpanCollectionFile(directory, File(partDir, STATE_JOURNAL_FILE_NAME), target.counters)
            .also { file = it }
    }

    private fun closeFile() {
        val open = file ?: return
        file = null
        errorHandler.guardSdkCode(SdkErrorType.StateJournalWriteFail, open::close)
    }

    private fun trackFailure(exc: Throwable) {
        errorHandler.trackSdkError(SdkErrorType.StateJournalWriteFail, exc)
    }

    private companion object {
        /** The record a journal opens with, stamping the layout its records are written in. */
        val JOURNAL_HEADER: ByteArray = StateJournal.ADAPTER.encode(StateJournal(format_version = FORMAT_VERSION))

        /** Bytes a write to the journal costs to end with [writtenAtMs], the time it was written. */
        fun writtenAtSize(writtenAtMs: Long): Long =
            StateJournal.ADAPTER.encodedSize(StateJournal(written_at_ms = writtenAtMs)).toLong()
    }
}
