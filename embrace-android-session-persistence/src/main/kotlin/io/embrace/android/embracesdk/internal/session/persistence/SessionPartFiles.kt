package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Version of the on-disk layout written by this SDK. Data persisted with any other version
 * cannot be read back.
 */
internal const val FORMAT_VERSION = 2

internal const val METADATA_FILE_NAME = "metadata.pb"

internal const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"

internal const val SPAN_SNAPSHOTS_FILE_NAME = "span_snapshots.pb"

/**
 * Field numbers of the records held in the span snapshots log, which is read and written one
 * record at a time rather than as a single message.
 */
internal const val SPAN_SNAPSHOT_VERSION_TAG = 1

internal const val SPAN_SNAPSHOT_RECORD_TAG = 2

/**
 * Writes [fileName] into [partDir] by encoding to a temporary file and then renaming it, so a
 * partially written file is never observed. Any file already at that path is replaced.
 *
 * At most [maxBytes] are written. A message larger than that fails the write, which leaves the file
 * already at that path untouched.
 *
 * The temporary file is always cleaned up, so a failed write leaves the directory as it was found.
 * Throws [IOException] if the file could not be written; callers are responsible for reporting that
 * as an internal error of the appropriate type.
 *
 * [onEncoded] runs once the new file is complete and immediately before it replaces [fileName], so
 * that a caller holding that file open can release it no earlier than it has to.
 */
internal fun writeAtomically(
    partDir: File,
    fileName: String,
    maxBytes: Long,
    onEncoded: () -> Unit = {},
    encode: (OutputStream) -> Unit,
) {
    SystemTrace.trace("mf-file-write-atomic") {
        val tmpFile = File.createTempFile(fileName, ".tmp", partDir)
        try {
            LimitedOutputStream(tmpFile.outputStream().buffered(), maxBytes).use(encode)
            onEncoded()
            if (!tmpFile.renameTo(File(partDir, fileName))) {
                throw IOException("Failed to rename $fileName")
            }
        } finally {
            tmpFile.delete()
        }
    }
}

/**
 * Fails the write once more than [limit] bytes have been written.
 */
private class LimitedOutputStream(
    private val delegate: OutputStream,
    private val limit: Long,
) : OutputStream() {

    private var written: Long = 0

    override fun write(b: Int) {
        checkLimit(1)
        delegate.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        checkLimit(len.toLong())
        delegate.write(b, off, len)
    }

    override fun flush() = delegate.flush()

    override fun close() = delegate.close()

    private fun checkLimit(count: Long) {
        written += count
        if (written > limit) {
            throw IOException(OVERSIZED_PART_FILE_MSG)
        }
    }
}
