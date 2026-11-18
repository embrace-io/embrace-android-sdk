package io.embrace.android.embracesdk.internal.delivery.storage

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * The largest attachment the SDK accepts for upload.
 */
const val MAX_ATTACHMENT_BYTES: Int = 1024 * 1024

/**
 * Room for the attachment ID header.
 */
private const val MAX_STORED_ATTACHMENT_HEADER_BYTES = 1024

private const val MAX_STORED_ATTACHMENT_BYTES = MAX_ATTACHMENT_BYTES + MAX_STORED_ATTACHMENT_HEADER_BYTES

fun storeAttachment(stream: OutputStream, attachment: ByteArray, id: String) {
    stream.use {
        it.write(id.toByteArray())
        it.write("\n".toByteArray())
        it.write(attachment)
    }
}

/**
 * Reads a stored attachment and its ID, or returns null if the stream holds more than the
 * max attachment size limit.
 */
fun loadAttachment(stream: InputStream): Pair<ByteArray, String>? {
    stream.use {
        val contents = it.readAtMost(MAX_STORED_ATTACHMENT_BYTES) ?: return null
        val start = contents.indexOfFirst { byte -> byte == '\n'.code.toByte() }
        val id = String(contents.sliceArray(0 until start))
        val attachment = contents.sliceArray(start + 1 until contents.size)
        return Pair(attachment, id)
    }
}

private fun InputStream.readAtMost(maxBytes: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count == -1) {
            return out.toByteArray()
        }
        total += count
        if (total > maxBytes) {
            return null
        }
        out.write(buffer, 0, count)
    }
}
