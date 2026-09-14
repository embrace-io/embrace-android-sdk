package io.embrace.android.embracesdk.internal.session.persistence

import okio.BufferedSource
import java.io.IOException

private const val UNSUPPORTED_VERSION_MSG = "Unsupported format version in session part file"

/** A record stamping the format version, which discards every record before it. */
private const val VERSION_TAG = 1

/** A record holding one span, which supersedes any earlier record for the same span ID. */
private const val SPAN_TAG = 2

/**
 * Decodes the append-only span snapshots held in [source], returning the latest known state of each
 * span still recording.
 */
internal fun readSpanSnapshots(
    source: BufferedSource,
    maxBytes: Long = MAX_PART_FILE_BYTES,
    maxRecordBytes: Long = MAX_RECORD_BYTES,
    maxSpans: Int = MAX_PERSISTED_SPANS,
): DecodedSpans = SnapshotDecoder(SpanCollectionReader(source, maxBytes, maxRecordBytes), maxSpans).read()

/**
 * Accumulates the latest state of each span as the records are read.
 */
private class SnapshotDecoder(private val collection: SpanCollectionReader, private val maxSpans: Int) {

    private val snapshots = LinkedHashMap<String, SpanProto>()
    private var corruption: Throwable? = null
    private var versioned = false
    private var truncated = false

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

    private fun readRecord(): Boolean = when (collection.nextTag()) {
        null -> false
        VERSION_TAG -> readRollup()
        SPAN_TAG -> readSnapshot()
        else -> collection.skipFrame()
    }

    private fun readRollup(): Boolean {
        val version = collection.readVarint32() ?: return false
        if (version != FORMAT_VERSION) {
            throw IOException(UNSUPPORTED_VERSION_MSG)
        }
        versioned = true
        snapshots.clear()
        return true
    }

    private fun readSnapshot(): Boolean {
        val record = collection.readRecord() ?: return false
        val span = try {
            SpanProto.ADAPTER.decode(record)
        } catch (exc: Exception) {
            // keep exc associated with first bad record, then continue
            corruption = corruption ?: exc
            return true
        }
        if (snapshots.size >= maxSpans && !snapshots.containsKey(span.span_id)) {
            truncated = true
            return false
        }
        snapshots[span.span_id] = span
        return true
    }
}
