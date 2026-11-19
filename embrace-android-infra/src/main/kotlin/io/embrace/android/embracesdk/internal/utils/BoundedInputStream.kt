package io.embrace.android.embracesdk.internal.utils

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads at most [maxBytes] from [delegate], throwing [IOException] with [message] on any read that
 * finds more.
 */
class BoundedInputStream(
    delegate: InputStream,
    maxBytes: Long,
    private val message: String,
) : FilterInputStream(delegate) {

    private var remaining: Long = maxBytes.coerceAtLeast(0)

    private var exceeded = false

    override fun read(): Int {
        if (remaining == 0L) {
            return checkExhausted()
        }
        val b = super.read()
        if (b != -1) {
            remaining--
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) {
            return 0
        }
        if (remaining == 0L) {
            return checkExhausted()
        }
        val count = super.read(b, off, minOf(len.toLong(), remaining).toInt())
        if (count > 0) {
            remaining -= count
        }
        return count
    }

    override fun skip(n: Long): Long {
        if (n <= 0L || remaining == 0L) {
            return 0
        }
        val skipped = super.skip(minOf(n, remaining)).coerceAtLeast(0)
        remaining -= skipped
        return skipped
    }

    override fun available(): Int = minOf(super.available().toLong(), remaining).toInt()

    override fun markSupported(): Boolean = false

    override fun mark(readlimit: Int) {}

    override fun reset(): Unit = throw IOException("mark/reset not supported")

    /**
     * Returns -1 if the delegate is at its end, and throws if it holds anything past the cap.
     * Once it has thrown it keeps throwing, as the probe consumed the byte that showed the excess.
     */
    private fun checkExhausted(): Int {
        if (!exceeded && super.read() == -1) {
            return -1
        }
        exceeded = true
        throw IOException(message)
    }
}
