package io.embrace.android.embracesdk.internal.utils

/**
 * Converts a byte array to a UTF-8 string, escaping non-encodable bytes as
 * 2-byte UTF-8 sequences, which will later be converted into unicode by JSON marshalling.
 * This allows us to send arbitrary binary data from the NDK
 * protobuf file without needing to encode it as Base64 (which compresses poorly).
 */
public fun ByteArray.toUTF8String(): String {
    val encoded = ByteArray(this.size * 2)
    var i = 0
    for (b in this) {
        val u = b.toInt() and 0xFF
        if (u < 128) {
            encoded[i++] = u.toByte()
            continue
        }
        encoded[i++] = (0xC0 or (u shr 6)).toByte()
        encoded[i++] = (0x80 or (u and 0x3F)).toByte()
    }
    return String(encoded.copyOf(i), Charsets.UTF_8)
}
