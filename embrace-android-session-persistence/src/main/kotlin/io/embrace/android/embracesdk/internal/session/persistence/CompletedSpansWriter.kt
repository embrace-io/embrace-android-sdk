package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Span
import java.io.File
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
    private val sessionsDir: Lazy<File>,
    private val sessionPartDirectorySource: () -> SessionPartDirectory?,
    private val logger: InternalLogger,
    private val maxBytes: Long = MAX_PART_FILE_BYTES,
) {

    private val lock = Any()

    @Volatile
    private var reportedOverflow = false

    /**
     * Appends [spans] to the log for the active session part, leaving the spans already logged in
     * place. A session part in which nothing completed has no log at all, which reconstruction
     * reads back as no completed spans.
     */
    fun write(spans: List<Span>): Boolean = synchronized(lock) {
        try {
            writeImpl(spans)
        } catch (exc: Throwable) {
            trackFailure(exc)
            false
        }
    }

    private fun writeImpl(spans: List<Span>): Boolean {
        val directory = sessionPartDirectorySource() ?: return false

        val partDir = File(sessionsDir.value, directory.dirName)
        if (!partDir.isDirectory) {
            trackFailure(IOException("Not a session part directory"))
            return false
        }
        val records = CompletedSpans(spans = spans.map(Span::toProto))
        val bytes = CompletedSpans.ADAPTER.encode(records)

        if (bytes.isNotEmpty() && !fits(partDir, bytes.size)) {
            if (!reportedOverflow) {
                reportedOverflow = true
                trackFailure(IOException(OVERSIZED_PART_FILE_MSG))
            }
            return false
        }
        appendTo(partDir, COMPLETED_SPANS_FILE_NAME, bytes)
        return true
    }

    private fun fits(partDir: File, byteCount: Int): Boolean =
        File(partDir, COMPLETED_SPANS_FILE_NAME).length() + byteCount <= maxBytes

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.CompletedSpansWriteFail, exc)
    }
}
