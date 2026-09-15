package io.embrace.android.embracesdk.internal.session.persistence

import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.File

/**
 * Reads the files a session part directory holds.
 */
internal class FileSessionPartSource(private val partDir: File) : SessionPartSource {

    override fun exists(file: SessionPartFile): Boolean = file.resolve().exists()

    override fun open(file: SessionPartFile): BufferedSource? {
        val src = file.resolve()
        return when {
            src.isFile -> src.source().buffer()
            else -> null
        }
    }

    override fun sizeBytes(file: SessionPartFile): Long = file.resolve().length()

    private fun SessionPartFile.resolve(): File = File(partDir, fileName)
}
