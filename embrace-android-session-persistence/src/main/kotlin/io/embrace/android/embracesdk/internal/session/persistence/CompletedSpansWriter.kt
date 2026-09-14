package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.io.File
import java.io.IOException

/**
 * Appends ended spans to the log a session part directory holds.
 *
 * The log is only ever added to, so the cost of a write is proportional to the spans being written
 * rather than to everything logged for the session part so far. Each record is self contained,
 * which lets [readCompletedSpans] recover every span logged before a process died mid-append.
 *
 * The session span of a session part is logged here too, once that part has ended.
 */
class CompletedSpansWriter(
    private val target: SessionPartWriteTarget,
    private val logger: InternalLogger,
    private val maxBytes: Long = MAX_PART_FILE_BYTES,
) {

    @Volatile
    private var reportedOverflow = false

    private var spanFile: SpanCollectionFile? = null

    /**
     * Appends [spans] to the log for the active session part, leaving the spans already logged in
     * place. A session part in which nothing completed has no log at all, which reconstruction
     * reads back as no completed spans.
     */
    fun write(spans: List<Span>): Boolean = SystemTrace.trace("mf-write-completed-spans") {
        try {
            writeImpl(spans)
        } catch (exc: Throwable) {
            trackFailure(exc)
            discardSpanFile()
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

        val bytes = CompletedSpans.ADAPTER.encode(buildCompletedSpans(spans))

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
    private fun spanFile(): SpanCollectionFile? {
        val directory = target.directory ?: return null
        spanFile?.let { open ->
            if (open.directory == directory) {
                return open
            }
            discardSpanFile()
        }
        val partDir = target.partDir(directory, ::trackFailure) ?: return null
        return SpanCollectionFile(directory, File(partDir, COMPLETED_SPANS_FILE_NAME)).also { spanFile = it }
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
}
