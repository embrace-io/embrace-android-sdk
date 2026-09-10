package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Appends ended spans to the log a session part directory holds.
 *
 * The log is only ever added to, so the cost of a write is proportional to the spans being written
 * rather than to everything logged for the session part so far. Each record is self contained,
 * which lets [readCompletedSpans] recover every span logged before a process died mid-append.
 *
 * The session span is persisted separately and should not be logged here. Callers are responsible
 * for filtering it out.
 */
class CompletedSpansWriter(
    private val target: SessionPartWriteTarget,
    private val logger: InternalLogger,
    private val maxBytes: Long = MAX_PART_FILE_BYTES,
) {

    @Volatile
    private var reportedOverflow = false

    private var spanFile: SpanFile? = null

    /**
     * Appends [spans] to the log for the active session part, leaving the spans already logged in
     * place. A session part in which nothing completed has no log at all, which reconstruction
     * reads back as no completed spans.
     */
    fun write(spans: List<Span>): Boolean = SystemTrace.trace("mf-write-completed-spans") {
        try {
            writeImpl(spans)
        } catch (exc: Throwable) {
            discardSpanFile()
            trackFailure(exc)
            false
        }
    }

    /**
     * Releases the file held open for the session part written to so far.
     */
    fun close() {
        discardSpanFile()
    }

    private fun writeImpl(spans: List<Span>): Boolean {
        val spanFile = spanFile() ?: return false

        val records = CompletedSpans(spans = spans.map(Span::toProto))
        val bytes = CompletedSpans.ADAPTER.encode(records)

        if (bytes.isNotEmpty() && spanFile.size + bytes.size > maxBytes) {
            if (!reportedOverflow) {
                reportedOverflow = true
                trackFailure(IOException(OVERSIZED_PART_FILE_MSG))
            }
            return false
        }
        spanFile.append(bytes)
        return true
    }

    /**
     * The file to append to, or null if the active session part has no directory on disk.
     */
    private fun spanFile(): SpanFile? {
        val directory = target.directory ?: return null
        spanFile?.let { open ->
            if (open.directory == directory) {
                return open
            }
            discardSpanFile()
        }
        val partDir = target.partDir(directory, ::trackFailure) ?: return null
        return SpanFile(directory, File(partDir, COMPLETED_SPANS_FILE_NAME)).also { spanFile = it }
    }

    private fun discardSpanFile() {
        val file = spanFile ?: return
        spanFile = null
        try {
            file.close()
        } catch (exc: IOException) {
            trackFailure(exc)
        }
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.CompletedSpansWriteFail, exc)
    }

    /**
     * The completed spans file for one session part, kept open across the appends made to it.
     */
    private class SpanFile(val directory: SessionPartDirectory, private val file: File) {

        private var stream: FileOutputStream? = null

        // assume file size doesn't change after first lookup
        var size: Long = file.length()
            private set

        /**
         * Appends [bytes] to the file, opening it if this is the first append.
         */
        fun append(bytes: ByteArray) {
            val stream = stream ?: FileOutputStream(file, true).also { stream = it }
            stream.write(bytes)
            size += bytes.size
        }

        fun close() {
            stream?.close()
        }
    }
}
