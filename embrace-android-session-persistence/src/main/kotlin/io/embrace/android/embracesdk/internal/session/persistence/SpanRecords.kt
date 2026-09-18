package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.Span

/** The record a span collection opens with, stamping the layout its spans are written in. */
internal val VERSION_HEADER: ByteArray =
    SpanCollection.ADAPTER.encode(SpanCollection(format_version = FORMAT_VERSION))

/** Bytes a collection costs before any span is written to it. */
internal val VERSION_HEADER_BYTES: Long = VERSION_HEADER.size.toLong()

/** Bytes [record] costs once framed as one span record of a collection. */
internal fun recordSize(record: SpanProto): Long =
    SpanCollection.ADAPTER.encodedSize(SpanCollection(spans = listOf(record))).toLong()

/**
 * The records to write for [spans], dropping any the collection cannot hold: one that would take it
 * past [budget], and one larger than [maxRecordBytes], which the reader stops at and so would cost
 * every record written after it.
 *
 * [onDrop] is called for each span dropped, and is expected to report only the first of them: a
 * collection that has filled up drops everything offered to it from then on.
 */
internal fun spanRecords(
    spans: List<Span>,
    budget: Long,
    maxRecordBytes: Long,
    onDrop: () -> Unit,
): List<SpanProto> {
    val written = ArrayList<SpanProto>(spans.size)
    var remaining = budget
    for (span in spans) {
        val record = span.toProto()
        val size = recordSize(record)
        if (size > remaining) {
            onDrop()
            break
        }
        if (size > maxRecordBytes) {
            onDrop()
        } else {
            remaining -= size
            written.add(record)
        }
    }
    return written
}
