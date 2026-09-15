package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.utils.FileWriteCounters
import java.io.File
import java.io.FileOutputStream

/**
 * An append-only collection of spans for one session part, kept open across the appends made to it.
 */
internal class SpanCollectionFile(
    val directory: SessionPartDirectory,
    private val file: File,
    private val counters: FileWriteCounters,
) {

    private var stream: FileOutputStream? = null

    // assume file size doesn't change after first lookup
    var size: Long = file.length()
        private set

    fun append(bytes: ByteArray) {
        val stream = stream ?: FileOutputStream(file, true).also { stream = it }
        stream.write(bytes)
        size += bytes.size
        if (bytes.isNotEmpty()) {
            counters.recordWrite(bytes.size.toLong())
        }
    }

    fun close() {
        stream?.close()
    }
}
