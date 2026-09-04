package io.embrace.android.embracesdk.internal.session.persistence

import com.squareup.wire.ProtoReader
import okio.BufferedSource
import java.io.EOFException

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
internal fun readCompletedSpans(source: BufferedSource, maxBytes: Long = MAX_PART_FILE_BYTES): List<SpanProto> {
    val spans = mutableListOf<SpanProto>()
    val reader = ProtoReader(source)
    reader.beginMessage()
    var remaining = maxBytes

    while (true) {
        val record = try {
            when (reader.nextTag()) {
                -1 -> return spans
                SPANS_TAG -> reader.readBytes()
                else -> {
                    reader.skip()
                    continue
                }
            }
        } catch (exc: EOFException) {
            return spans
        }
        remaining -= record.size
        if (remaining < 0) {
            return spans
        }
        spans.add(SpanProto.ADAPTER.decode(record))
    }
}
