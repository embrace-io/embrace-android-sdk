package io.embrace.android.embracesdk.internal.session.persistence

import okio.ByteString
import java.io.IOException

/**
 * Decodes one span collection file. Subclasses are responsible for processing individual span records.
 * Records are decoded one at a time.
 *
 * A process can die part way through an append, so a collection that stops mid-record is expected
 * and is not reported: every record written in full before it is kept. A record that is all present
 * but does not decode is corruption, as is a frame that cannot be followed at all. Both are
 * reported alongside the records that did read back.
 */
internal abstract class SpanRecordDecoder(protected val collection: SpanCollectionReader) {

    private var corruption: Throwable? = null
    private var versioned = false

    /** Whether reading stopped at a limit rather than at the end of what was written. */
    protected var truncated = false

    /** The failure that stopped the read, preferring the first bad record over a lost frame. */
    protected val failure: Throwable? get() = corruption ?: collection.corruption

    fun read(): DecodedSpans {
        var reading = true
        while (reading) {
            reading = readFrame()
        }
        if (!versioned) {
            throw IOException(UNSUPPORTED_VERSION_MSG, failure)
        }
        return DecodedSpans(decoded(), failure, truncated)
    }

    /** Decodes the record the frame just announced holds, returning false to stop reading. */
    protected abstract fun readRecord(): Boolean

    /** Discards everything read before the version record just seen. */
    protected abstract fun reset()

    /** The spans read back, in the order they are delivered. */
    protected abstract fun decoded(): MutableList<SpanProto>

    /**
     * The span [record] holds, or null if it does not decode. The first such failure is kept and
     * reported with the records that did read back, so one bad record costs only itself.
     */
    protected fun decodeSpan(record: ByteString): SpanProto? = try {
        SpanProto.ADAPTER.decode(record)
    } catch (exc: Exception) {
        corruption = corruption ?: exc
        null
    }

    /** Stops where the collection did, which is truncation only if a limit is what stopped it. */
    protected fun stop(): Boolean {
        truncated = truncated || collection.stoppedAtLimit
        return false
    }

    /** Stops because a limit of this decoder's own was reached. */
    protected fun truncate(): Boolean {
        truncated = true
        return false
    }

    private fun readFrame(): Boolean {
        val tag = collection.nextTag() ?: return stop()
        return when (tag) {
            SPAN_COLLECTION_VERSION_TAG -> readVersion()
            SPAN_COLLECTION_RECORD_TAG -> readRecord()
            else -> collection.skipFrame()
        }
    }

    private fun readVersion(): Boolean {
        val version = collection.readVarint32() ?: return stop()
        if (version != FORMAT_VERSION) {
            throw IOException(UNSUPPORTED_VERSION_MSG)
        }
        versioned = true
        reset()
        return true
    }
}
