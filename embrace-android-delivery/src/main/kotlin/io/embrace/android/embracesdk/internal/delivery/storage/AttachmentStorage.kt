package io.embrace.android.embracesdk.internal.delivery.storage

import java.io.InputStream
import java.io.OutputStream

fun storeAttachment(stream: OutputStream, attachment: ByteArray, id: String) {
    stream.use {
        it.write(id.toByteArray())
        it.write("\n".toByteArray())
        it.write(attachment)
    }
}

fun loadAttachment(stream: InputStream): Pair<ByteArray, String>? {
    stream.use {
        val contents = it.readBytes()
        val start = contents.indexOfFirst { byte -> byte == '\n'.code.toByte() }
        val id = String(contents.sliceArray(0 until start))
        val attachment = contents.sliceArray(start + 1 until contents.size)
        return Pair(attachment, id)
    }
}
