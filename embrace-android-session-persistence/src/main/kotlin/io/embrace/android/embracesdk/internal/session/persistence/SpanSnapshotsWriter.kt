package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Bytes a log costs before any span is written to it.
 */
private val ROLLUP_HEADER_BYTES: Long =
    SpanSnapshots.ADAPTER.encodedSize(SpanSnapshots(format_version = FORMAT_VERSION)).toLong()

/**
 * Writes the in-flight spans for a session part to its directory as an append-only log.
 *
 * A log holds the latest known state of each recording span: a later record for a span ID
 * supersedes an earlier one, and a rollup record ([write]) discards everything logged before it.
 * That lets a change to one span cost one append rather than a rewrite of every span in flight.
 *
 * The log is rolled up once the appends since the last rollup have outgrown it, which bounds the
 * file at roughly twice the size of the live set. A rollup is written atomically, so a process that
 * dies during one leaves the previous log intact, and a process that dies mid-append leaves a torn
 * tail that [readSpanSnapshots] tolerates.
 *
 * The spans logged here include the session span, until the session part ends.
 */
class SpanSnapshotsWriter(
    private val target: SessionPartWriteTarget,
    private val logger: InternalLogger,
    private val maxBytes: Long = MAX_PART_FILE_BYTES,
    private val maxRecordBytes: Long = MAX_RECORD_BYTES,
) {

    @Volatile
    private var reportedDrop = false

    @Volatile
    private var reportedOverflow = false

    /**
     * Whether the log has grown as far as it can, so nothing more can be logged for this part.
     */
    private var full = false

    private var log: SnapshotLog? = null

    /**
     * Rolls the log up, replacing whatever is on disk with a version record followed by [spans].
     */
    fun write(spans: List<Span>): Boolean = SystemTrace.trace("mf-write-span-snapshots") {
        try {
            writeImpl(spans)
        } catch (exc: Throwable) {
            // the log the rollup failed to replace is still on disk and still open, so it is left
            // in place to be appended to rather than discarded
            trackFailure(exc)
            false
        }
    }

    /**
     * Appends the latest state of the spans that changed, leaving the records already logged in
     * place. [liveSpans] supplies every recording span, and is only read when the log has grown
     * enough to be worth rolling up.
     */
    fun append(spans: List<Span>, liveSpans: () -> List<Span>): Boolean =
        SystemTrace.trace("mf-append-span-snapshots") {
            try {
                appendImpl(spans, liveSpans)
            } catch (exc: Throwable) {
                discardLog()
                reportAppendFailure(exc)
                false
            }
        }

    /**
     * Reports an append that failed. An append writes through the file it holds open rather than
     * through [target], so a part directory that has gone is reported through the target instead
     * of as the failure it surfaced as, which is what gives the part up rather than leaving every
     * later write to fail the same way.
     */
    private fun reportAppendFailure(exc: Throwable) {
        val directory = target.directory
        if (directory == null || target.partDir(directory, ::trackFailure) != null) {
            trackFailure(exc)
        }
    }

    /**
     * Releases the file held open for the session part written to so far.
     */
    fun close() {
        discardLog()
    }

    private fun writeImpl(spans: List<Span>): Boolean {
        val directory = target.directory ?: return false
        val partDir = target.partDir(directory, ::trackFailure) ?: return false

        val snapshots = SpanSnapshots(
            format_version = FORMAT_VERSION,
            spans = recordsFor(spans, maxBytes - ROLLUP_HEADER_BYTES),
        )
        // the open stream points at the file the rollup replaces, so appends made through it
        // afterwards would land on an unlinked inode and be lost. It is held until the rollup has
        // been encoded, so that a rollup which fails leaves a log that can still be appended to.
        writeAtomically(partDir, SPAN_SNAPSHOTS_FILE_NAME, maxBytes, onEncoded = ::discardLog) { stream ->
            SpanSnapshots.ADAPTER.encode(stream, snapshots)
        }
        val file = File(partDir, SPAN_SNAPSHOTS_FILE_NAME)
        log = SnapshotLog(
            directory = directory,
            file = file,
            rollupBytes = file.length(),
            rollupRecords = snapshots.spans.size,
            maxBytes = maxBytes,
        )
        full = false
        return true
    }

    private fun appendImpl(spans: List<Span>, liveSpans: () -> List<Span>): Boolean {
        if (spans.isEmpty()) {
            return true
        }
        val log = openLog() ?: return write(liveSpans())
        if (full) {
            return false
        }
        val records = recordsFor(spans, maxBytes)
        if (records.isEmpty()) {
            return false
        }

        // a record with no version field is an update rather than a rollup
        val bytes = SpanSnapshots.ADAPTER.encode(SpanSnapshots(spans = records))
        if (log.outgrewRollup(bytes.size, records.size) && write(liveSpans())) {
            return true
        }

        // either the log has room, or the rollup that would have made room could not be written.
        // In that case it is appended to until it is full, so that the spans it holds stay as
        // fresh as the file allows rather than freezing at the last rollup.
        val open = this.log ?: return false
        if (open.wouldOverflow(bytes.size)) {
            full = true
            reportOverflow()
            return false
        }
        open.append(bytes, records.size)
        return true
    }

    /**
     * The records to write for [spans], dropping any span the log cannot hold: one that would take
     * it past [budget], and one whose own record is larger than [maxRecordBytes], which the reader
     * stops at and so would cost every record logged after it.
     */
    private fun recordsFor(spans: List<Span>, budget: Long): List<SpanProto> {
        val records = ArrayList<SpanProto>(spans.size)
        var remaining = budget
        for (span in spans) {
            val record = span.toProto()
            val size = SpanProto.ADAPTER.encodedSizeWithTag(SPAN_SNAPSHOT_RECORD_TAG, record).toLong()
            if (size > remaining) {
                reportDrop()
                break
            }
            if (size > maxRecordBytes) {
                reportDrop()
            } else {
                remaining -= size
                records.add(record)
            }
        }
        return records
    }

    /**
     * The log to append to, or null if there is none yet for the session part being written to.
     */
    private fun openLog(): SnapshotLog? {
        val open = log ?: return null
        if (open.directory == target.directory) {
            return open
        }
        discardLog()
        full = false
        return null
    }

    private fun discardLog() {
        val open = log ?: return
        log = null
        try {
            open.close()
        } catch (exc: IOException) {
            trackFailure(exc)
        }
    }

    private fun reportDrop() {
        if (!reportedDrop) {
            reportedDrop = true
            trackFailure(IOException(DROPPED_SPAN_SNAPSHOT_MSG))
        }
    }

    private fun reportOverflow() {
        if (!reportedOverflow) {
            reportedOverflow = true
            trackFailure(IOException(OVERSIZED_PART_FILE_MSG))
        }
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SpanSnapshotsWriteFail, exc)
    }

    /**
     * The span snapshots log for one session part, kept open across the appends made to it.
     */
    private class SnapshotLog(
        val directory: SessionPartDirectory,
        private val file: File,
        private val rollupBytes: Long,
        private val rollupRecords: Int,
        private val maxBytes: Long,
    ) {

        private var stream: FileOutputStream? = null

        private var appendedBytes: Long = 0

        private var appendedRecords: Int = 0

        /**
         * Whether writing [records] records of [incoming] bytes should roll the log up rather than
         * append to it. Rolling up once the appends outgrow the rollup they are based on caps write
         * amplification at roughly two, and keeps the log inside the size and record count that can
         * be read back without the limits ever being enforced against a partially written record.
         */
        fun outgrewRollup(incoming: Int, records: Int): Boolean {
            val bytes = appendedBytes + incoming
            return bytes > maxOf(rollupBytes, MIN_ROLLUP_BYTES) ||
                rollupBytes + bytes > maxBytes ||
                rollupRecords + appendedRecords + records > MAX_LOGGED_SNAPSHOT_RECORDS
        }

        /**
         * Whether writing [incoming] bytes would take the log past the size a part file can be.
         */
        fun wouldOverflow(incoming: Int): Boolean = rollupBytes + appendedBytes + incoming > maxBytes

        fun append(bytes: ByteArray, records: Int) {
            val stream = stream ?: FileOutputStream(file, true).also { stream = it }
            stream.write(bytes)
            appendedBytes += bytes.size
            appendedRecords += records
        }

        fun close() {
            stream?.close()
        }
    }
}
