package io.embrace.android.embracesdk.internal.compression

import java.io.OutputStream
import java.util.zip.GZIPOutputStream

/**
 * This Output Stream will only compress the data if it's not already compressed.
 * If the data is already compressed, it will just write it to the output stream.
 */
internal class ConditionalGzipOutputStream(
    private val outputStream: OutputStream,
) : OutputStream() {

    private val magicNumberBuffer = mutableListOf<Byte>()
    private var drainedBuffer: Boolean = false
    private lateinit var impl: OutputStream

    override fun write(b: Int) {
        if (magicNumberBuffer.size < 2) {
            magicNumberBuffer.add(b.toByte())
        } else {
            drainBufferIfNeeded()
            impl.write(b)
        }
    }

    override fun close() {
        drainBufferIfNeeded()
        impl.close()
    }

    override fun flush() {
        drainBufferIfNeeded()
        impl.flush()
    }

    /**
     * If the buffer is not drained, it means that we have not yet written the first two bytes
     * of the file, which are the gzip magic number. We need to write them before writing the rest
     * of the file.
     */
    private fun drainBufferIfNeeded() {
        if (!drainedBuffer) {
            impl = generateOutputStream()
            magicNumberBuffer.forEach {
                impl.write(it.toInt())
            }
            drainedBuffer = true
        }
    }

    private fun generateOutputStream(): OutputStream {
        return if (isCompressed(magicNumberBuffer)) {
            outputStream.buffered()
        } else {
            GZIPOutputStream(outputStream.buffered())
        }
    }

    /**
     * Verifies if the file is compressed by reading the first two bytes and checking for the gzip
     * magic number, it's a sequence of two bytes that appears at the beginning of a gzip-compressed file.
     * First byte: 0x1F
     * Second byte: 0x8B
     */
    private fun isCompressed(buffer: List<Byte>): Boolean {
        if (buffer.size < 2) {
            return false
        }
        // Check for the gzip magic number (0x1F8B)
        return buffer[0] == 0x1F.toByte() && buffer[1] == 0x8B.toByte()
    }
}
