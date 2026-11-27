package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.state.StateJournalRecord
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.io.File
import java.io.IOException
import kotlin.random.Random

/**
 * Appends the records of a session part's state journal to the file its directory holds.
 *
 * The file is only ever added to, so the cost of a write is proportional to the records being written. Each record is a frame of
 * its own, which lets [readStateJournal] recover every record written before a process died mid-append. Each append ends with the
 * time it was written.
 *
 * The record that opens a session part is given the ids of the span the journal is later rendered as, once, as it is written, so
 * that every rendering of the file is the same span.
 *
 * What the file holds is always the start of what was recorded, with no hole in it: once a record cannot be written, for any
 * reason, nothing more is appended for the session part. The file then has no closing record, so it reads back as cut short.
 */
class StateJournalWriter(
    private val target: SessionPartWriteTarget,
    private val errorHandler: ErrorHandler,
    private val maxBytes: Long = MAX_PART_FILE_BYTES,
    private val maxRecordBytes: Long = MAX_RECORD_BYTES,
    private val random: Random = Random.Default,
) : AutoCloseable {

    @Volatile
    private var reportedDrop = false

    /**
     * Whether a record of this session part could not be written. Nothing is appended after that.
     */
    private var stopped = false

    private var file: SpanCollectionFile? = null

    /**
     * Appends [records], in order, in one write to the journal of the active session part. A record for any other session part is
     * skipped. Returns false once a record could not be written, after which nothing more is appended.
     */
    fun write(records: List<StateJournalRecord>): Boolean = SystemTrace.trace("mf-write-state-journal") {
        if (stopped) {
            return@trace false
        }
        try {
            writeImpl(records)
        } catch (exc: Throwable) {
            stopped = true
            closeFile()
            target.reportWriteFailure(exc, ::trackFailure)
            false
        }
    }

    /**
     * Releases the file held open for the session part written to so far.
     */
    override fun close() {
        closeFile()
    }

    private fun writeImpl(records: List<StateJournalRecord>): Boolean {
        if (records.isEmpty()) {
            return true
        }
        val directory = target.directory ?: return false
        val matching = records.filter { it.sessionPartId == directory.sessionPartId }
        if (matching.isEmpty()) {
            return true
        }
        val file = openFile(directory) ?: return false
        val fresh = file.size == 0L
        val writtenAt = target.currentTimeMs()
        val header = if (fresh) HEADER_BYTES else 0L
        var remaining = maxBytes - file.size - header - writtenAtSize(writtenAt)
        val entries = ArrayList<StateJournalEntry>(matching.size)
        for (record in matching) {
            val entry = record.toEntry(::randomTraceId, ::randomSpanId)
            val size = StateJournalFile.ADAPTER.encodedSize(StateJournalFile(entries = listOf(entry))).toLong()
            if (size > remaining || size > maxRecordBytes) {
                // this record and everything after it is left out, so that what is on disk has no hole
                stopped = true
                reportDrop()
                break
            }
            remaining -= size
            entries.add(entry)
        }
        if (entries.isEmpty()) {
            return false
        }
        val appended = StateJournalFile(
            format_version = if (fresh) FORMAT_VERSION else 0,
            entries = entries,
            written_at_ms = writtenAt,
        )
        file.append(StateJournalFile.ADAPTER.encode(appended))
        return !stopped
    }

    /**
     * The file to append to, or null if the active session part has no directory on disk.
     */
    private fun openFile(directory: SessionPartDirectory): SpanCollectionFile? {
        file?.let { open ->
            if (open.directory == directory) {
                return open
            }
            closeFile()
        }
        val partDir = target.partDir(directory, ::trackFailure) ?: return null
        return SpanCollectionFile(directory, File(partDir, STATE_JOURNAL_FILE_NAME), target.counters)
            .also { file = it }
    }

    private fun closeFile() {
        val open = file ?: return
        file = null
        errorHandler.guardSdkCode(SdkErrorType.StateJournalWriteFail, open::close)
    }

    private fun reportDrop() {
        if (!reportedDrop) {
            reportedDrop = true
            trackFailure(IOException(DROPPED_STATE_JOURNAL_ENTRY_MSG))
        }
    }

    private fun trackFailure(exc: Throwable) {
        errorHandler.trackSdkError(SdkErrorType.StateJournalWriteFail, exc)
    }

    private fun randomTraceId(): String = randomHex(TRACE_ID_BYTES)

    private fun randomSpanId(): String = randomHex(SPAN_ID_BYTES)

    /**
     * Random lower case hex of [byteCount] bytes that is never all zero, which OpenTelemetry does not allow as an id.
     */
    private fun randomHex(byteCount: Int): String {
        var bytes: ByteArray
        do {
            bytes = random.nextBytes(byteCount)
        } while (bytes.all { it == 0.toByte() })
        val hex = CharArray(byteCount * 2)
        bytes.forEachIndexed { i, byte ->
            val value = byte.toInt() and BYTE_MASK
            hex[i * 2] = HEX_DIGITS[value shr NIBBLE_BITS]
            hex[i * 2 + 1] = HEX_DIGITS[value and NIBBLE_MASK]
        }
        return String(hex)
    }

    private companion object {
        val HEADER_BYTES: Long =
            StateJournalFile.ADAPTER.encodedSize(StateJournalFile(format_version = FORMAT_VERSION)).toLong()

        const val TRACE_ID_BYTES = 16
        const val SPAN_ID_BYTES = 8
        const val BYTE_MASK = 0xFF
        const val NIBBLE_MASK = 0xF
        const val NIBBLE_BITS = 4
        const val HEX_DIGITS = "0123456789abcdef"
    }
}
