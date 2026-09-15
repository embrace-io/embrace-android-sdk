package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

internal class CountingOutputStreamTest {

    private val sink = ByteArrayOutputStream()

    @Test
    fun `nothing written counts zero`() {
        assertEquals(0L, CountingOutputStream(sink).written)
    }

    @Test
    fun `single byte writes are counted`() {
        val stream = CountingOutputStream(sink)
        stream.write(1)
        stream.write(2)
        assertEquals(2L, stream.written)
        assertArrayEquals(byteArrayOf(1, 2), sink.toByteArray())
    }

    @Test
    fun `array writes count the length written rather than the length of the array`() {
        val stream = CountingOutputStream(sink)
        stream.write(byteArrayOf(1, 2, 3, 4), 1, 2)
        assertEquals(2L, stream.written)
        assertArrayEquals(byteArrayOf(2, 3), sink.toByteArray())
    }

    @Test
    fun `bytes buffered by a wrapping stream are counted once it is flushed`() {
        val stream = CountingOutputStream(sink)
        val buffered = stream.buffered()
        buffered.write(byteArrayOf(1, 2, 3))
        assertEquals(0L, stream.written)

        buffered.flush()
        assertEquals(3L, stream.written)
    }

    @Test
    fun `close is delegated`() {
        var closed = false
        val stream = CountingOutputStream(
            object : ByteArrayOutputStream() {
                override fun close() {
                    closed = true
                }
            },
        )
        stream.close()
        assertTrue(closed)
    }

    @Test
    fun `a write the delegate rejects is not counted`() {
        val stream = CountingOutputStream(
            object : OutputStream() {
                override fun write(b: Int): Unit = throw IOException("disk full")

                override fun write(b: ByteArray, off: Int, len: Int): Unit = throw IOException("disk full")
            },
        )

        assertThrows(IOException::class.java) { stream.write(1) }
        assertThrows(IOException::class.java) { stream.write(byteArrayOf(1, 2, 3)) }
        assertEquals(0L, stream.written)
    }
}
