package io.embrace.android.embracesdk.internal.utils

import java.io.OutputStream

/**
 * Counts the bytes written through it
 */
class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {

    var written: Long = 0
        private set

    override fun write(b: Int) {
        written++
        delegate.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        written += len
        delegate.write(b, off, len)
    }

    override fun flush() = delegate.flush()

    override fun close() = delegate.close()
}
