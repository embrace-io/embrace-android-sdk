package io.embrace.android.embracesdk.fakes

/**
 * Encodes Span/Trace ID bytes as a hex string.
 */
internal fun ByteArray.toHexString(): String {
    val result = StringBuilder(size * 2)
    for (b in this) {
        val i = b.toInt() and 0xFF
        result.append(i.toString(16).padStart(2, '0'))
    }
    return result.toString()
}
