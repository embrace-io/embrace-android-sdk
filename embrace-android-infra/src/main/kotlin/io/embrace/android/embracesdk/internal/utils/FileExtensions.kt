package io.embrace.android.embracesdk.internal.utils

import java.io.DataInputStream
import java.io.File

/**
 * Return the contents of the file as a [ByteArray] if it's smaller than [maxBytes] bytes. Otherwise, return the first [maxBytes].
 * This will re-throw any exceptions or errors encountered while reading the file.
 */
fun File.readHead(maxBytes: Int): ByteArray = DataInputStream(inputStream()).use { stream ->
    val size = minOf(length(), maxBytes.coerceAtLeast(0).toLong()).toInt()
    val bytes = ByteArray(size)
    stream.readFully(bytes)
    bytes
}
