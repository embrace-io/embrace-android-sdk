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
    private val maxRecordBytes: Long = MAX_RECORD_BYTES,
    private val maxRecords: Int = MAX_PERSISTED_SPANS,
) {

    @Volatile
    private var reportedDrop = false

    private var file: SpanCollectionFile? = null

    /**
     * Records written to the log held open. An error that forces the file to be discarded restarts
     * the count, so this bounds what one open file writes rather than the log as a whole. The byte
     * budget is re-read from disk on reopening and stays exact.
     */
    private var records = 0

    /**
     * Appends [spans] to the log for the active session part, leaving the spans already logged in
     * place. A session part in which nothing completed has no log at all, which reconstruction
     * reads back as no completed spans.
     */
    fun write(spans: List<Span>): Boolean = SystemTrace.trace("mf-write-completed-spans") {
        try {
            writeImpl(spans)
        } catch (exc: Throwable) {
            discardFile()
            target.reportWriteFailure(exc, ::trackFailure)
            false
        }
    }

    /**
     * Releases the file held open for the session part written to so far.
     */
    fun close() {
        discardFile()
    }

    private fun writeImpl(spans: List<Span>): Boolean {
        if (spans.isEmpty()) {
            return true
        }
        val file = openFile() ?: return false
        val header = if (file.size == 0L) VERSION_HEADER else ByteArray(0)
        val budget = maxBytes - file.size - header.size
        val written = spanRecords(spans, budget, maxRecordBytes, ::reportDrop).capped()
        if (written.isEmpty()) {
            return false
        }
        file.append(header + SpanCollection.ADAPTER.encode(SpanCollection(spans = written)))
        records += written.size
        return true
    }

    /**
     * The records of this batch the log can still hold. Reconstruction refuses to read past
     * [maxRecords] spans of a session part, so anything beyond it costs the write without ever
     * being delivered.
     */
    private fun List<SpanProto>.capped(): List<SpanProto> {
        val allowed = (maxRecords - records).coerceAtLeast(0)
        if (size <= allowed) {
            return this
        }
        reportDrop()
        return take(allowed)
    }

    /**
     * The file to append to, or null if the active session part has no directory on disk.
     */
    private fun openFile(): SpanCollectionFile? {
        val directory = target.directory ?: return null
        file?.let { open ->
            if (open.directory == directory) {
                return open
            }
            discardFile()
        }
        val partDir = target.partDir(directory, ::trackFailure) ?: return null
        return SpanCollectionFile(directory, File(partDir, COMPLETED_SPANS_FILE_NAME), target.counters)
            .also { file = it }
    }

    private fun discardFile() {
        val open = file ?: return
        file = null
        records = 0
        try {
            open.close()
        } catch (exc: IOException) {
            trackFailure(exc)
        }
    }

    private fun reportDrop() {
        if (!reportedDrop) {
            reportedDrop = true
            trackFailure(IOException(DROPPED_COMPLETED_SPAN_MSG))
        }
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.CompletedSpansWriteFail, exc)
    }
}
