package io.embrace.android.embracesdk.internal.session.persistence

import com.squareup.wire.ProtoReader
import okio.BufferedSource
import java.io.EOFException
import java.io.IOException

/**
 * Decodes the append-only log of in-flight span snapshots held in [source].
 *
 * The log is read in the order it was written: a version record rolls the log up, discarding
 * everything logged before it, and a snapshot record supersedes any earlier record for the same
 * span ID. What is returned is therefore the latest known state of each span still recording.
 *
 * A process can die part way through an append, so a log that stops mid-record is expected and is
 * not reported: every record written in full before it is returned. A record that is all present
 * but does not decode is corruption and is reported. A log that runs past any of the limits is
 * read as far as the limit allows and reported as truncated. A log holding no version record at
 * all was written by an SDK using a different on-disk layout and is rejected.
 */
internal fun readSpanSnapshots(
    source: BufferedSource,
    maxBytes: Long = MAX_PART_FILE_BYTES,
    maxRecordBytes: Long = MAX_RECORD_BYTES,
    maxSpans: Int = MAX_PERSISTED_SPANS,
    maxRecords: Int = MAX_SNAPSHOT_RECORDS,
): DecodedSpans {
    val log = SnapshotLogDecoder(maxBytes, maxRecordBytes, maxSpans, maxRecords)
    val reader = ProtoReader(source)
    reader.beginMessage()
    try {
        @Suppress("EmptyWhileBlock")
        while (log.readRecord(reader)) {
        }
    } catch (exc: EOFException) {
        // the log stops mid-record: the process died part way through an append
    }
    return log.decoded()
}

/**
 * Accumulates the latest state of each span as the records of one log are read.
 */
private class SnapshotLogDecoder(
    private val maxBytes: Long,
    private val maxRecordBytes: Long,
    private val maxSpans: Int,
    private val maxRecords: Int,
) {

    private val snapshots = LinkedHashMap<String, SpanProto>()
    private var corruption: Throwable? = null
    private var versioned = false
    private var truncated = false
    private var records = 0
    private var remaining = maxBytes

    /**
     * Reads the next record, returning false once the log has been read as far as it can be.
     */
    fun readRecord(reader: ProtoReader): Boolean = when (reader.nextTag()) {
        -1 -> false
        SPAN_SNAPSHOT_VERSION_TAG -> readRollup(reader)
        SPAN_SNAPSHOT_RECORD_TAG -> readSnapshot(reader)
        else -> {
            reader.skip()
            true
        }
    }

    fun decoded(): DecodedSpans {
        if (!versioned) {
            throw IOException("Unsupported format version in session part file")
        }
        return DecodedSpans(snapshots.values.toMutableList(), corruption, truncated)
    }

    private fun readRollup(reader: ProtoReader): Boolean {
        if (reader.readVarint32() != FORMAT_VERSION) {
            throw IOException("Unsupported format version in session part file")
        }
        versioned = true
        snapshots.clear()
        return true
    }

    private fun readSnapshot(reader: ProtoReader): Boolean {
        // a record past any of these is not skipped: what follows it in the log can no longer be
        // located, so the rest of the log is lost with it
        if (++records > maxRecords || reader.nextFieldMinLengthInBytes() > maxRecordBytes) {
            return truncate()
        }
        val record = reader.readBytes()
        remaining -= record.size
        if (remaining < 0) {
            return truncate()
        }
        val span = try {
            SpanProto.ADAPTER.decode(record)
        } catch (exc: Exception) {
            // keep exc associated with first bad record, then continue
            corruption = corruption ?: exc
            return true
        }
        if (snapshots.size >= maxSpans && !snapshots.containsKey(span.span_id)) {
            return truncate()
        }
        snapshots[span.span_id] = span
        return true
    }

    /**
     * Stops reading, reporting what was read as an incomplete view of the log.
     */
    private fun truncate(): Boolean {
        truncated = true
        return false
    }
}
