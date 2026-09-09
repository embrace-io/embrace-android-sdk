package io.embrace.android.embracesdk.internal.utils

import java.io.File
import java.io.RandomAccessFile

/**
 * Return the contents of the file as a [ByteArray] if it's smaller than [maxBytes] bytes. Otherwise, return the first [maxBytes].
 * This will re-throw any exceptions or errors encountered while reading the file.
 */
fun File.readHead(maxBytes: Int): ByteArray = RandomAccessFile(this, "r").use { file ->
    val size = minOf(file.length(), maxBytes.coerceAtLeast(0).toLong()).toInt()
    val bytes = ByteArray(size)
    file.readFully(bytes)
    bytes
}
