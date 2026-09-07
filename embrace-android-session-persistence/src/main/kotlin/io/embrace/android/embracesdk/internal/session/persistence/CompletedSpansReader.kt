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
internal fun readCompletedSpans(source: BufferedSource, maxBytes: Long = MAX_PART_FILE_BYTES): DecodedSpans {
    val spans = mutableListOf<SpanProto>()
    var corruption: Throwable? = null
    val reader = ProtoReader(source)
    reader.beginMessage()
    var remaining = maxBytes

    while (true) {
        val record = try {
            when (reader.nextTag()) {
                -1 -> return DecodedSpans(spans, corruption)
                SPANS_TAG -> reader.readBytes()
                else -> {
                    reader.skip()
                    continue
                }
            }
        } catch (exc: EOFException) {
            return DecodedSpans(spans, corruption)
        }
        remaining -= record.size
        if (remaining < 0) {
            return DecodedSpans(spans, corruption)
        }
        try {
            spans.add(SpanProto.ADAPTER.decode(record))
        } catch (exc: Exception) {
            // keep exc associated with first bad record, then continue
            corruption = corruption ?: exc
        }
    }
}
