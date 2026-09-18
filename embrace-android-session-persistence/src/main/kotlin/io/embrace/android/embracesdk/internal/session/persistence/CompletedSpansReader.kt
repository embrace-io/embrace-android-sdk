package io.embrace.android.embracesdk.internal.session.persistence

import okio.BufferedSource

/**
 * Decodes the append-only log of completed spans held in [source].
 *
 * Every record is kept, in the order it was appended, because a span only reaches this log once it
 * has ended and so is never superseded by a later record.
 */
internal fun readCompletedSpans(
    source: BufferedSource,
    maxBytes: Long = MAX_PART_FILE_BYTES,
    maxRecordBytes: Long = MAX_RECORD_BYTES,
    maxSpans: Int = MAX_PERSISTED_SPANS,
): DecodedSpans = CompletedSpansDecoder(
    SpanCollectionReader(source, maxBytes, maxRecordBytes),
    maxSpans,
).read()

/**
 * Accumulates the ended spans in the order they were logged.
 */
private class CompletedSpansDecoder(
    collection: SpanCollectionReader,
    private val maxSpans: Int,
) : SpanRecordDecoder(collection) {

    private val spans = mutableListOf<SpanProto>()

    override fun decoded(): MutableList<SpanProto> = spans

    override fun reset() {
        spans.clear()
    }

    override fun readRecord(): Boolean {
        if (spans.size >= maxSpans) {
            return truncate()
        }
        val record = collection.readRecord() ?: return stop()
        decodeSpan(record)?.let(spans::add)
        return true
    }
}
