package io.embrace.android.embracesdk.internal.session.persistence

import com.squareup.wire.FieldEncoding
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
    private val source: BufferedSource,
    private val maxBytes: Long,
    private val maxRecordBytes: Long,
) {

    private val reader = ProtoReader(source)
    private var remaining = maxBytes

    /** The length the frame just announced declares, or -1 if it could not be peeked. */
    private var declaredLength = -1L

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
        declaredLength = peekDeclaredLength()
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

    /** The 64-bit varint held by the frame just announced. */
    fun readVarint64(): Long? = try {
        reader.readVarint64()
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
            if (reader.peekFieldEncoding() == FieldEncoding.LENGTH_DELIMITED && declaredLength !in 0..maxRecordBytes) {
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

    /**
     * Wire caps nextFieldMinLengthInBytes() at the bytes buffered, so the declared length is read
     * from the frame header before ProtoReader consumes it.
     */
    private fun peekDeclaredLength(): Long {
        val header = source.peek()
        val key = header.readVarint()
        if (key < 0 || (key and WIRE_TYPE_MASK) != WIRE_TYPE_LENGTH_DELIMITED) {
            return -1
        }
        return header.readVarint()
    }

    /** A varint read from the source, or -1 if it is torn or malformed. */
    private fun BufferedSource.readVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < Long.SIZE_BITS && !exhausted()) {
            val byte = readByte().toLong()
            result = result or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0L) {
                return result
            }
            shift += 7
        }
        return -1
    }

    private fun stopAtLimit(): ByteString? {
        stoppedAtLimit = true
        return null
    }

    private fun recordCorruption(exc: IOException) {
        corruption = corruption ?: exc
    }

    private companion object {
        const val WIRE_TYPE_MASK = 0x7L
        const val WIRE_TYPE_LENGTH_DELIMITED = 2L
    }
}
