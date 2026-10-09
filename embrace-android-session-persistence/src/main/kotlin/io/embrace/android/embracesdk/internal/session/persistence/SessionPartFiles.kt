package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Version of the on-disk layout written by this SDK. Data persisted with any other version
 * cannot be read back.
 */
internal const val FORMAT_VERSION = 3

internal const val METADATA_FILE_NAME = "metadata.pb"

internal const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"

internal const val SPAN_SNAPSHOTS_FILE_NAME = "span_snapshots.pb"

/** Field numbers of the records held in a span collection file, which is read one record at a time. */
internal const val SPAN_COLLECTION_VERSION_TAG = 1

internal const val SPAN_COLLECTION_RECORD_TAG = 2

/**
 * Trace section around an immediate sync to disk after an atomic write.
 * */
internal const val FILE_SYNC_SECTION = "mf-file-sync"

/**
 * Writes [fileName] into [partDir] by encoding to a temporary file and then renaming it, so a
 * partially written file is never observed. Any file already at that path is replaced.
 *
 * At most [maxBytes] are written. A message larger than that fails the write, which leaves the file
 * already at that path untouched.
 *
 * The temporary file is always cleaned up, so a failed write leaves the directory as it was found.
 * Its name is derived from [fileName], so one orphaned by a process that died mid-write is
 * reclaimed by the next write of that file rather than adding to what is held on disk.
 *
 * Optionally, allow the write to be synced down to the file system immediately. It's more costly to do,
 * but worth it in cases that happen infrequently and where a write failure is more consequential.
 *
 * Throws [IOException] if the file could not be written; callers are responsible for reporting that
 * as an internal error of the appropriate type. The write is recorded with [target] once the file is
 * in place.
 */
internal fun writeAtomically(
    partDir: File,
    fileName: String,
    maxBytes: Long,
    target: SessionPartWriteTarget,
    syncImmediately: Boolean = false,
    encode: (OutputStream) -> Unit,
) {
    SystemTrace.trace("mf-file-write-atomic") {
        val tmpFile = File(partDir, "$fileName.tmp")
        try {
            val fileStream = tmpFile.outputStream()
            val stream = LimitedOutputStream(fileStream.buffered(), maxBytes)
            stream.use {
                encode(it)
                if (syncImmediately) {
                    it.flush()
                    SystemTrace.trace(FILE_SYNC_SECTION) {
                        fileStream.fd.sync()
                    }
                }
            }
            val file = File(partDir, fileName)
            if (!tmpFile.renameTo(file)) {
                throw IOException("Failed to rename $fileName")
            }
            target.recordWrite(file, stream.written)
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

    var written: Long = 0
        private set

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
