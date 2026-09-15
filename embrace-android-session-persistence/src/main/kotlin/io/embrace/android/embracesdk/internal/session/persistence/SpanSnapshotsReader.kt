package io.embrace.android.embracesdk.internal.session.persistence

import okio.BufferedSource
import java.io.IOException

private const val UNSUPPORTED_VERSION_MSG = "Unsupported format version in session part file"

/**
 * Decodes the append-only span snapshots held in [source], returning the latest known state of each
 * span still recording.
 */
internal fun readSpanSnapshots(
    source: BufferedSource,
    maxBytes: Long = MAX_PART_FILE_BYTES,
    maxRecordBytes: Long = MAX_RECORD_BYTES,
    maxSpans: Int = MAX_PERSISTED_SPANS,
    maxRecords: Int = MAX_PERSISTED_SPANS,
): DecodedSpans = SnapshotDecoder(
    SpanCollectionReader(source, maxBytes, maxRecordBytes),
    maxSpans,
    maxRecords,
).read()

/**
 * Accumulates the latest state of each span as the records are read.
 */
private class SnapshotDecoder(
    private val collection: SpanCollectionReader,
    private val maxSpans: Int,
    private val maxRecords: Int,
) {

    private val snapshots = LinkedHashMap<String, SpanProto>()
    private var corruption: Throwable? = null
    private var versioned = false
    private var truncated = false
    private var records = 0

    fun read(): DecodedSpans {
        var reading = true
        while (reading) {
            reading = readRecord()
        }
        if (!versioned) {
            throw IOException(UNSUPPORTED_VERSION_MSG)
        }
        return DecodedSpans(snapshots.values.toMutableList(), corruption, truncated)
    }

    private fun readRecord(): Boolean {
        val tag = collection.nextTag() ?: return stop()
        return when (tag) {
            SPAN_SNAPSHOT_VERSION_TAG -> readRollup()
            SPAN_SNAPSHOT_RECORD_TAG -> readSnapshot()
            else -> collection.skipFrame()
        }
    }

    private fun readRollup(): Boolean {
        val version = collection.readVarint32() ?: return stop()
        if (version != FORMAT_VERSION) {
            throw IOException(UNSUPPORTED_VERSION_MSG)
        }
        versioned = true
        snapshots.clear()
        return true
    }

    private fun readSnapshot(): Boolean {
        if (++records > maxRecords) {
            return truncate()
        }
        val record = collection.readRecord() ?: return stop()
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

    /** Stops where the collection did, which is truncation only if a limit is what stopped it. */
    private fun stop(): Boolean {
        truncated = truncated || collection.stoppedAtLimit
        return false
    }

    private fun truncate(): Boolean {
        truncated = true
        return false
    }
}
