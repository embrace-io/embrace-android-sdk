package io.embrace.android.embracesdk.internal.session.persistence

import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Version of the on-disk layout written by this SDK. Data persisted with any other version
 * cannot be read back.
 */
internal const val FORMAT_VERSION = 1

internal const val MANIFEST_FILE_NAME = "manifest.pb"

internal const val METADATA_FILE_NAME = "metadata.pb"

internal const val SESSION_SPAN_FILE_NAME = "session_span.pb"

/**
 * Writes [fileName] into [partDir] by encoding to a temporary file and then renaming it, so a
 * partially written file is never observed. Any file already at that path is replaced.
 *
 * The temporary file is always cleaned up, so a failed write leaves the directory as it was found.
 * Throws [IOException] if the file could not be written; callers are responsible for reporting that
 * as an internal error of the appropriate type.
 */
internal fun writeAtomically(partDir: File, fileName: String, encode: (OutputStream) -> Unit) {
    val tmpFile = File.createTempFile(fileName, ".tmp", partDir)
    try {
        tmpFile.outputStream().buffered().use(encode)
        if (!tmpFile.renameTo(File(partDir, fileName))) {
            throw IOException("Failed to rename $fileName")
        }
    } finally {
        tmpFile.delete()
    }
}
