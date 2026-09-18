package io.embrace.android.embracesdk.internal.session.persistence

import okio.BufferedSource

/**
 * Decodes the append-only span snapshots held in [source], returning the latest known state of each
 * span still recording.
 *
 * [supersededIds] holds the spans already read back from the completed spans log. A completed span
 * always supersedes a snapshot of it, so its records are skipped rather than spending [maxSpans] on
 * a span that is discarded once the payload is assembled.
 */
internal fun readSpanSnapshots(
    source: BufferedSource,
    maxBytes: Long = MAX_PART_FILE_BYTES,
    maxRecordBytes: Long = MAX_RECORD_BYTES,
    maxSpans: Int = MAX_PERSISTED_SPANS,
    maxRecords: Int = MAX_PERSISTED_SPANS,
    supersededIds: Set<String> = emptySet(),
): DecodedSpans = SnapshotDecoder(
    SpanCollectionReader(source, maxBytes, maxRecordBytes),
    maxSpans,
    maxRecords,
    supersededIds,
).read()

/**
 * Accumulates the latest state of each span as the records are read.
 */
private class SnapshotDecoder(
    collection: SpanCollectionReader,
    private val maxSpans: Int,
    private val maxRecords: Int,
    private val supersededIds: Set<String>,
) : SpanRecordDecoder(collection) {

    private val snapshots = LinkedHashMap<String, SpanProto>()
    private var records = 0

    override fun decoded(): MutableList<SpanProto> = snapshots.values.toMutableList()

    override fun reset() {
        snapshots.clear()
    }

    override fun readRecord(): Boolean {
        if (++records > maxRecords) {
            return truncate()
        }
        val record = collection.readRecord() ?: return stop()
        val span = decodeSpan(record) ?: return true
        if (span.span_id in supersededIds) {
            return true
        }
        if (snapshots.size >= maxSpans && !snapshots.containsKey(span.span_id)) {
            truncated = true
            return true
        }
        snapshots[span.span_id] = span
        return true
    }
}
