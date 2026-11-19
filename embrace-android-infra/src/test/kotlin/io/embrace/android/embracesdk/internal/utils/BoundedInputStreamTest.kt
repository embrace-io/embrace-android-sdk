package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

internal class BoundedInputStreamTest {

    @Test
    fun `a stream within the cap is read whole`() {
        val bytes = ByteArray(10) { it.toByte() }
        assertArrayEquals(bytes, bounded(bytes, 10).readBytes())
    }

    @Test
    fun `a stream under the cap is read whole`() {
        val bytes = ByteArray(5) { it.toByte() }
        assertArrayEquals(bytes, bounded(bytes, 10).readBytes())
    }

    @Test
    fun `a stream over the cap throws rather than truncating`() {
        val exc = assertThrows(IOException::class.java) { bounded(ByteArray(11), 10).readBytes() }
        assertEquals(MSG, exc.message)
    }

    @Test
    fun `single byte reads throw past the cap`() {
        val stream = bounded(byteArrayOf(1, 2, 3), 2)
        assertEquals(1, stream.read())
        assertEquals(2, stream.read())
        assertThrows(IOException::class.java) { stream.read() }
    }

    @Test
    fun `single byte reads end at the cap when the delegate ends there`() {
        val stream = bounded(byteArrayOf(1, 2), 2)
        assertEquals(1, stream.read())
        assertEquals(2, stream.read())
        assertEquals(-1, stream.read())
    }

    @Test
    fun `array reads are clamped to the bytes remaining`() {
        val stream = bounded(ByteArray(8) { it.toByte() }, 3)
        val buffer = ByteArray(8)
        assertEquals(3, stream.read(buffer, 0, 8))
        assertThrows(IOException::class.java) { stream.read(buffer, 0, 8) }
    }

    @Test
    fun `skipped bytes count towards the cap`() {
        val stream = bounded(ByteArray(8), 4)
        assertEquals(4L, stream.skip(100))
        assertThrows(IOException::class.java) { stream.read() }
    }

    @Test
    fun `reads keep throwing once the cap is exceeded`() {
        val stream = bounded(byteArrayOf(1, 2, 3), 2)
        stream.read()
        stream.read()
        assertThrows(IOException::class.java) { stream.read() }
        assertThrows(IOException::class.java) { stream.read() }
        assertThrows(IOException::class.java) { stream.read(ByteArray(4), 0, 4) }
    }

    @Test
    fun `a zero cap accepts an empty stream and rejects any byte`() {
        assertEquals(-1, bounded(ByteArray(0), 0).read())
        assertThrows(IOException::class.java) { bounded(byteArrayOf(1), 0).read() }
    }

    @Test
    fun `a negative cap is treated as a zero cap`() {
        assertEquals(-1, bounded(ByteArray(0), -1).read())
        assertThrows(IOException::class.java) { bounded(byteArrayOf(1), -1).read() }
    }

    @Test
    fun `a negative skip does not move the count`() {
        val stream = bounded(ByteArray(4), 2)
        assertEquals(0L, stream.skip(-10))
        assertEquals(2L, stream.skip(10))
        assertThrows(IOException::class.java) { stream.read() }
    }

    @Test
    fun `close is delegated`() {
        var closed = false
        val delegate = object : ByteArrayInputStream(ByteArray(0)) {
            override fun close() {
                closed = true
            }
        }
        BoundedInputStream(delegate, 1, MSG).close()
        assertTrue(closed)
    }

    private fun bounded(bytes: ByteArray, maxBytes: Long) = BoundedInputStream(ByteArrayInputStream(bytes), maxBytes, MSG)

    private companion object {
        const val MSG = "too big"
    }
}
