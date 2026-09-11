package io.embrace.android.embracesdk.internal.perfetto

import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

/** How many bytes are decompressed to decide what a file holds. */
private const val HEADER_BYTES = 8

/**
 * Field 1, wire type 2. A perfetto trace is a bare sequence of length-delimited TracePackets, so it
 * starts with this tag followed by the length of its first packet.
 */
private const val PACKET_TAG = 0x0a

/** A protobuf length is a uint32, so its varint never runs to more than five bytes. */
private const val MAX_VARINT_BYTES = 5

private const val CONTINUATION_BIT = 0x80

/**
 * Reports whether [trace] is a gzipped perfetto trace, by decompressing its first few bytes.
 * Anything else - not gzip, gzip of something other than a trace, unreadable - is [UNKNOWN].
 * Nothing beyond the header is read: this only decides whether analysing the file is worth
 * attempting.
 */
internal fun validateTrace(trace: File): TraceFormat = try {
    GZIPInputStream(trace.inputStream()).use { gzip ->
        traceFormat(gzip.readNBytes(HEADER_BYTES))
    }
} catch (exc: IOException) {
    TraceFormat.UNKNOWN
}

/**
 * Classifies the decompressed leading bytes of a file.
 */
internal fun traceFormat(header: ByteArray): TraceFormat {
    if (header.byteAt(0) != PACKET_TAG) {
        return TraceFormat.UNKNOWN
    }
    // the first packet's length follows the tag as a base 128 varint
    val terminated = (1..MAX_VARINT_BYTES).any { index ->
        val byte = header.byteAt(index)
        byte >= 0 && byte and CONTINUATION_BIT == 0
    }
    return if (terminated) TraceFormat.PERFETTO else TraceFormat.UNKNOWN
}

/** Returns the unsigned value of the byte at [index], or -1 when the header is shorter than that. */
private fun ByteArray.byteAt(index: Int): Int = getOrNull(index)?.toInt()?.and(0xFF) ?: -1
