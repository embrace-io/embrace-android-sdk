package io.embrace.android.embracesdk.internal.compression

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal class ConditionalGzipOutputStreamTest {

    private lateinit var finalOs: ByteArrayOutputStream
    private lateinit var conditionalGzipOutputStream: ConditionalGzipOutputStream

    @Before
    fun setUp() {
        finalOs = ByteArrayOutputStream()
        conditionalGzipOutputStream = ConditionalGzipOutputStream(finalOs)
    }

    @Test
    fun `test writing uncompressed data, executes compression`() {
        val input = "Hello world!"
        conditionalGzipOutputStream.use {
            it.write(input.toByteArray())
        }

        val uncompressed = String(GZIPInputStream(finalOs.toByteArray().inputStream()).readBytes())
        assertEquals(input, uncompressed)
    }

    @Test
    fun `test writing compressed data, doesn't compress again`() {
        val input = "Hello world!"
        val os = ByteArrayOutputStream()
        GZIPOutputStream(os).use {
            it.write(input.toByteArray())
        }

        conditionalGzipOutputStream.use {
            it.write(os.toByteArray())
        }

        val uncompressed = String(GZIPInputStream(finalOs.toByteArray().inputStream()).readBytes())
        assertEquals(input, uncompressed)
    }

    @Test
    fun `test writing one byte only, compresses correctly`() {
        val input = "H"
        conditionalGzipOutputStream.use {
            it.write(input.toByteArray()[0].toInt())
        }

        val uncompressed = String(GZIPInputStream(finalOs.toByteArray().inputStream()).readBytes())
        assertEquals("H", uncompressed)
    }

    @Test
    fun `test writing no bytes, returns correctly`() {
        val input = ""
        conditionalGzipOutputStream.use {
            it.write(input.toByteArray())
        }

        val result = String(finalOs.toByteArray().inputStream().readBytes())
        assertEquals("", result)
    }
}
