package io.embrace.android.embracesdk.internal.session.persistence

import com.squareup.wire.ProtoReader
import okio.BufferedSource
import okio.ByteString
import java.io.EOFException

/**
 * Reads the records of an append-only collection of spans. Frames are pulled one at a time, so
 * each caller can apply its own limits before the next is consumed.
 */
internal class SpanCollectionReader(
    source: BufferedSource,
    maxBytes: Long,
    private val maxRecordBytes: Long,
) {

    private val reader = ProtoReader(source)
    private var remaining = maxBytes

    init {
        reader.beginMessage()
    }

    /** The tag of the next frame, or null once there is no further frame to read. */
    fun nextTag(): Int? = try {
        reader.nextTag().takeIf { it != -1 }
    } catch (exc: EOFException) {
        null
    }

    /** Skips the frame just announced, which is not charged against the size budget. */
    fun skipFrame(): Boolean = try {
        reader.skip()
        true
    } catch (exc: EOFException) {
        false
    }

    /** The varint held by the frame just announced. */
    fun readVarint32(): Int? = try {
        reader.readVarint32()
    } catch (exc: EOFException) {
        null
    }

    /**
     * The record held by the frame just announced, or null if it is torn or past a limit.
     */
    fun readRecord(): ByteString? {
        val record = try {
            if (reader.nextFieldMinLengthInBytes() > maxRecordBytes) {
                return null
            }
            reader.readBytes()
        } catch (exc: EOFException) {
            return null
        }
        remaining -= record.size
        return record.takeIf { remaining >= 0 }
    }
}
