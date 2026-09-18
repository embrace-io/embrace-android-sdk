package io.embrace.android.embracesdk.internal.session.persistence

import com.squareup.wire.ProtoReader
import okio.BufferedSource
import okio.ByteString
import java.io.EOFException
import java.io.IOException

/**
 * Reads the records of an append-only collection of spans. Frames are pulled one at a time, so
 * each caller can apply its own limits before the next is consumed.
 *
 * Reading stops at the end of the collection, at a limit, or at a frame that cannot be followed.
 */
internal class SpanCollectionReader(
    source: BufferedSource,
    private val maxBytes: Long,
    private val maxRecordBytes: Long,
) {

    private val reader = ProtoReader(source)
    private var remaining = maxBytes

    /** Whether reading stopped at a limit rather than at the end of what was written. */
    var stoppedAtLimit: Boolean = false
        private set

    /** The failure that stopped reading, if the frames could no longer be followed. */
    var corruption: IOException? = null
        private set

    init {
        reader.beginMessage()
    }

    /** The tag of the next frame, or null once there is no further frame to read. */
    fun nextTag(): Int? = try {
        reader.nextTag().takeIf { it != -1 }
    } catch (exc: EOFException) {
        null
    } catch (exc: IOException) {
        recordCorruption(exc)
        null
    }

    /** Skips the frame just announced, which is not charged against the size budget. */
    fun skipFrame(): Boolean = try {
        reader.skip()
        true
    } catch (exc: EOFException) {
        false
    } catch (exc: IOException) {
        recordCorruption(exc)
        false
    }

    /** The varint held by the frame just announced. */
    fun readVarint32(): Int? = try {
        reader.readVarint32()
    } catch (exc: EOFException) {
        null
    } catch (exc: IOException) {
        recordCorruption(exc)
        null
    }

    /**
     * The record held by the frame just announced, or null if it is torn, corrupt, or past a limit.
     */
    fun readRecord(): ByteString? {
        val record = try {
            if (reader.nextFieldMinLengthInBytes() > maxRecordBytes) {
                return stopAtLimit()
            }
            reader.readBytes()
        } catch (exc: EOFException) {
            return null
        } catch (exc: IOException) {
            recordCorruption(exc)
            return null
        }
        remaining -= record.size
        return if (remaining < 0) stopAtLimit() else record
    }

    private fun stopAtLimit(): ByteString? {
        stoppedAtLimit = true
        return null
    }

    private fun recordCorruption(exc: IOException) {
        corruption = corruption ?: exc
    }
}
