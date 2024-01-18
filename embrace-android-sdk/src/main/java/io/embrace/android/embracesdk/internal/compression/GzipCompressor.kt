package io.embrace.android.embracesdk.internal.compression

import io.embrace.android.embracesdk.comms.api.SerializationAction
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

/**
 * Compresses data using GZIP.
 */
internal class GzipCompressor(
    private val logger: InternalEmbraceLogger
) : Compressor {

    override fun compress(outputStream: OutputStream, action: SerializationAction) {
        try {
            GZIPOutputStream(outputStream.buffered()).use { bufferedGzipStream ->
                action(bufferedGzipStream)
            }
        } catch (e: Exception) {
            logger.logError("Failed to compress payload", e)
        }
    }

    /**
     * Verifies if the file is compressed by reading the first two bytes and checking for the gzip
     * magic number, it's a sequence of two bytes that appears at the beginning of a gzip-compressed file.
     * First byte: 0x1F
     * Second byte: 0x8B
     */
    override fun isCompressed(file: File): Boolean {
        val bufferSize = 2 // Read the first two bytes for the gzip magic number
        val buffer = ByteArray(bufferSize)

        FileInputStream(file).buffered().use { fileInputStream ->
            // Read the first two bytes
            fileInputStream.mark(bufferSize)
            fileInputStream.read(buffer, 0, bufferSize)
            fileInputStream.reset()
        }

        // Check for the gzip magic number (0x1F8B)
        return buffer[0] == 0x1F.toByte() && buffer[1] == 0x8B.toByte()
    }
}
