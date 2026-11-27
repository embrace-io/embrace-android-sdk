package io.embrace.android.embracesdk.internal.session.persistence

import java.io.IOException

private const val VERSION_FIELD = 1
private const val RECORDS_FIELD = 2
private const val WIRE_TYPE_VARINT = 0
private const val WIRE_TYPE_LENGTH_DELIMITED = 2
private const val VARINT_PAYLOAD_BITS = 7
private const val VARINT_PAYLOAD_MASK = 0x7f
private const val VARINT_CONTINUATION = 0x80
private const val MAX_VARINT_SHIFT = 63
private const val TRUNCATED_RECORD_MSG = "State journal ends in a record cut short"

/**
 * The records read from a state journal, and what stopped the read if it did not reach the end of the file cleanly.
 */
class DecodedStateJournal(
    val records: List<StateJournalRecord>,
    val corruption: Throwable?,
)

/**
 * Reads every complete record of a state journal, one at a time, so that a record cut short by a process dying
 * mid-append, or anything after it, loses only that much. Returns null if the journal was written with a format this
 * SDK cannot read.
 */
fun readStateJournal(bytes: ByteArray): DecodedStateJournal? {
    val reader = JournalReader(bytes)
    val corruption = try {
        while (reader.hasMore()) {
            if (!reader.readField()) {
                return null
            }
        }
        null
    } catch (exc: IOException) {
        exc
    }
    return DecodedStateJournal(reader.records, corruption)
}

private class JournalReader(private val bytes: ByteArray) {
    val records = mutableListOf<StateJournalRecord>()
    private var pos = 0
    private var versionChecked = false

    fun hasMore(): Boolean = pos < bytes.size

    /**
     * Reads one top-level field. Returns false if the journal has a format version this SDK cannot read, and throws
     * if the field is cut short or not one a journal holds.
     */
    fun readField(): Boolean {
        val tag = readVarint()
        val field = (tag ushr 3).toInt()
        val wireType = (tag and 0x7).toInt()
        return when {
            field == VERSION_FIELD && wireType == WIRE_TYPE_VARINT -> {
                versionChecked = readVarint().toInt() == FORMAT_VERSION
                versionChecked
            }
            field == RECORDS_FIELD && wireType == WIRE_TYPE_LENGTH_DELIMITED && versionChecked -> {
                readRecord()
                true
            }
            else -> throw IOException("Unexpected field in state journal")
        }
    }

    private fun readRecord() {
        val length = readVarint()
        if (length < 0 || length > bytes.size - pos) {
            throw IOException(TRUNCATED_RECORD_MSG)
        }
        val end = pos + length.toInt()
        records.add(StateJournalRecord.ADAPTER.decode(bytes.copyOfRange(pos, end)))
        pos = end
    }

    private fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (pos < bytes.size && shift <= MAX_VARINT_SHIFT) {
            val b = bytes[pos++].toInt() and 0xff
            result = result or ((b and VARINT_PAYLOAD_MASK).toLong() shl shift)
            if (b and VARINT_CONTINUATION == 0) {
                return result
            }
            shift += VARINT_PAYLOAD_BITS
        }
        throw IOException(TRUNCATED_RECORD_MSG)
    }
}
