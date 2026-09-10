package io.embrace.android.embracesdk.benchmark

import java.io.OutputStream

/**
 * Discards everything written to it and counts the bytes it was given.
 */
internal class CountingOutputStream : OutputStream() {

    var bytesWritten: Long = 0L
        private set

    override fun write(b: Int) {
        bytesWritten++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        bytesWritten += len
    }

    override fun close() = Unit

    fun reset() {
        bytesWritten = 0L
    }
}
