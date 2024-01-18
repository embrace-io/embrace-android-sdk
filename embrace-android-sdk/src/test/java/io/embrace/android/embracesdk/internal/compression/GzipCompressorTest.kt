package io.embrace.android.embracesdk.internal.compression

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

internal class GzipCompressorTest {

    private lateinit var compressor: GzipCompressor

    @Before
    fun setUp() {
        compressor = GzipCompressor(logger = InternalEmbraceLogger())
    }

    @Test
    fun testCompression() {
        val input = "Hello World!"
        val output = ByteArrayOutputStream()
        compressor.compress(output) { stream ->
            stream.write(input.toByteArray())
        }
        val compressed = output.toByteArray()
        val uncompressed = String(GZIPInputStream(compressed.inputStream()).readBytes())
        assertEquals(input, uncompressed)
    }
}
