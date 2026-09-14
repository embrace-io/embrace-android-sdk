package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.io.File
import java.io.IOException

/** Bytes the file costs before any span is written to it. */
private val ROLLUP_HEADER_BYTES: Long =
    SpanSnapshots.ADAPTER.encodedSize(SpanSnapshots(format_version = FORMAT_VERSION)).toLong()

/** Bytes [record] costs once framed as one span record of the file. */
private fun recordSize(record: SpanProto): Long =
    SpanSnapshots.ADAPTER.encodedSize(SpanSnapshots(spans = listOf(record))).toLong()

/**
 * Writes the in-flight spans for a session part to its directory, appending to what is already
 * there rather than rewriting it.
 *
 * A later record for a span ID supersedes an earlier one, and a rollup ([write]) discards
 * everything written before it, so a change to one span costs one append rather than a rewrite of
 * every span in flight. An append past [maxRecords] or [maxBytes] rolls the file up instead, which
 * bounds both its size and the work of reading it back.
 *
 * A rollup is written atomically, so a process that dies during one leaves the previous file
 * intact, and one that dies mid-append leaves a torn tail that the reader tolerates.
 *
 * The spans written here include the session span, until the session part ends.
 */
class SpanSnapshotsWriter(
    private val target: SessionPartWriteTarget,
    private val logger: InternalLogger,
    private val maxBytes: Long = MAX_PART_FILE_BYTES,
    private val maxRecordBytes: Long = MAX_RECORD_BYTES,
    private val maxRecords: Int = MAX_PERSISTED_SPANS,
) {

    @Volatile
    private var reportedDrop = false
    private var file: SpanCollectionFile? = null
    private var records = 0

    /**
     * Writes the state of all in-flight spans, discarding any previous persisted info.
     */
    fun write(spans: List<Span>): Boolean = SystemTrace.trace("mf-write-span-snapshots") {
        try {
            writeImpl(spans)
        } catch (exc: Throwable) {
            trackFailure(exc)
            false
        }
    }

    /**
     * Appends the latest state of the spans that changed, leaving the records already written
     * in place. [liveSpans] supplies every recording span, and is only read on a rollup.
     */
    fun append(spans: List<Span>, liveSpans: () -> List<Span>): Boolean =
        SystemTrace.trace("mf-append-span-snapshots") {
            try {
                appendImpl(spans, liveSpans)
            } catch (exc: Throwable) {
                discardFile()
                reportAppendFailure(exc)
                false
            }
        }

    fun close() {
        discardFile()
    }

    private fun writeImpl(spans: List<Span>): Boolean {
        val directory = target.directory ?: return false
        val partDir = target.partDir(directory, ::trackFailure) ?: return false
        val written = recordsFor(spans, maxBytes - ROLLUP_HEADER_BYTES)

        discardFile()
        writeAtomically(partDir, SPAN_SNAPSHOTS_FILE_NAME, maxBytes) { stream ->
            SpanSnapshots.ADAPTER.encode(stream, SpanSnapshots(format_version = FORMAT_VERSION, spans = written))
        }
        file = SpanCollectionFile(directory, File(partDir, SPAN_SNAPSHOTS_FILE_NAME))
        records = written.size
        return true
    }

    private fun appendImpl(spans: List<Span>, liveSpans: () -> List<Span>): Boolean {
        if (spans.isEmpty()) {
            return true
        }
        // spans that started in an earlier session part never report a change in this one, so
        // the file is seeded with the live set rather than built from changes alone
        val file = openFile() ?: return write(liveSpans())

        val appended = recordsFor(spans, maxBytes)
        if (appended.isEmpty()) {
            return false
        }
        // a record with no version field is an update rather than a rollup
        val bytes = SpanSnapshots.ADAPTER.encode(SpanSnapshots(spans = appended))
        if (exceedsLimits(file, bytes.size, appended.size)) {
            return write(liveSpans())
        }
        file.append(bytes)
        records += appended.size
        return true
    }

    /**
     * Whether writing [count] records of [incoming] bytes should roll the file up rather than
     * append to it. Capping records bounds the reader's work at the spans a session part can
     * deliver, and bounds write amplification at the appends made between one rollup and the next.
     */
    private fun exceedsLimits(file: SpanCollectionFile, incoming: Int, count: Int): Boolean =
        records + count > maxRecords || file.size + incoming > maxBytes

    /**
     * The records to write for [spans], dropping any span the file cannot hold: one that would
     * take it past [budget], and one larger than [maxRecordBytes], which the reader stops at and so
     * would cost every record written after it.
     */
    private fun recordsFor(spans: List<Span>, budget: Long): List<SpanProto> {
        val written = ArrayList<SpanProto>(spans.size)
        var remaining = budget
        for (span in spans) {
            val record = span.toProto()
            val size = recordSize(record)
            if (size > remaining) {
                reportDrop()
                break
            }
            if (size > maxRecordBytes) {
                reportDrop()
            } else {
                remaining -= size
                written.add(record)
            }
        }
        return written
    }

    /** The file to append to, or null if there is none yet for the session part written to. */
    private fun openFile(): SpanCollectionFile? {
        val open = file ?: return null
        if (open.directory == target.directory) {
            return open
        }
        discardFile()
        return null
    }

    private fun discardFile() {
        val open = file ?: return
        file = null
        records = 0
        try {
            open.close()
        } catch (exc: IOException) {
            trackFailure(exc)
        }
    }

    /**
     * Reports a failed append. An append writes through the file it holds open rather than through
     * [target], so a part directory that has gone is reported through the target instead, which
     * gives the part up rather than leaving every later write to fail the same way.
     */
    private fun reportAppendFailure(exc: Throwable) {
        val directory = target.directory
        if (directory == null || target.partDir(directory, ::trackFailure) != null) {
            trackFailure(exc)
        }
    }

    private fun reportDrop() {
        if (!reportedDrop) {
            reportedDrop = true
            trackFailure(IOException(DROPPED_SPAN_SNAPSHOT_MSG))
        }
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SpanSnapshotsWriteFail, exc)
    }
}
