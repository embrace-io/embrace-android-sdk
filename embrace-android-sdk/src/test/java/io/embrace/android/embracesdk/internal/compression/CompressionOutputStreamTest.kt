package io.embrace.android.embracesdk.internal.compression

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal class CompressionOutputStreamTest {

    private lateinit var finalOs: ByteArrayOutputStream
    private lateinit var compressionOutputStream: CompressionOutputStream

    @Before
    fun setUp() {
        finalOs = ByteArrayOutputStream()
        compressionOutputStream = CompressionOutputStream(finalOs)
    }

    @Test
    fun `test writing uncompressed data, executes compression`() {
        val input = "Hello world!"
        compressionOutputStream.use {
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

        compressionOutputStream.use {
            it.write(os.toByteArray())
        }

        val uncompressed = String(GZIPInputStream(finalOs.toByteArray().inputStream()).readBytes())
        assertEquals(input, uncompressed)
    }

    @Test
    fun `test writing one byte only, compresses correctly`() {
        val input = "H"
        compressionOutputStream.use {
            it.write(input.toByteArray()[0].toInt())
        }

        val uncompressed = String(GZIPInputStream(finalOs.toByteArray().inputStream()).readBytes())
        assertEquals("H", uncompressed)
    }

    @Test
    fun `test writing no bytes, returns correctly`() {
        val input = ""
        compressionOutputStream.use {
            it.write(input.toByteArray())
        }

        val uncompressed = String(GZIPInputStream(finalOs.toByteArray().inputStream()).readBytes())
        assertEquals("", uncompressed)
    }
}
