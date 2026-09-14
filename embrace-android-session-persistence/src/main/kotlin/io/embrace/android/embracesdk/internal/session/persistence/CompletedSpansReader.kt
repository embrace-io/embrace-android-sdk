package io.embrace.android.embracesdk.internal.session.persistence

import okio.BufferedSource

private const val SPANS_TAG = 1

/**
 * Decodes the append-only log of completed spans held in [source].
 *
 * Records are decoded one at a time rather than with [CompletedSpans.ADAPTER], because this avoids
 * a single bad record taking down the entire batch.
 *
 * A process can die part way through an append, so a log that stops mid-record is expected and is
 * not reported: every record written in full before it is returned. A record that is all
 * present but does not decode is corruption and throws.
 */
internal fun readCompletedSpans(
    source: BufferedSource,
    maxBytes: Long = MAX_PART_FILE_BYTES,
    maxRecordBytes: Long = MAX_RECORD_BYTES,
    maxSpans: Int = MAX_PERSISTED_SPANS,
): DecodedSpans {
    val spans = mutableListOf<SpanProto>()
    var corruption: Throwable? = null
    val collection = SpanCollectionReader(source, maxBytes, maxRecordBytes)

    while (true) {
        val record = when (collection.nextTag()) {
            null -> return DecodedSpans(spans, corruption)
            SPANS_TAG -> {
                if (spans.size >= maxSpans) {
                    return DecodedSpans(spans, corruption, spanLimitReached = true)
                }
                collection.readRecord() ?: return DecodedSpans(spans, corruption)
            }
            else -> {
                if (!collection.skipFrame()) {
                    return DecodedSpans(spans, corruption)
                }
                continue
            }
        }
        try {
            spans.add(SpanProto.ADAPTER.decode(record))
        } catch (exc: Exception) {
            // keep exc associated with first bad record, then continue
            corruption = corruption ?: exc
        }
    }
}
