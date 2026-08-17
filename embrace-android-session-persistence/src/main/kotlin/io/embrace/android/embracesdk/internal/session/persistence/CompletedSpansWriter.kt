package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Writes completed spans to a file. Records are appended rather than rewriting the entire file which
 * avoids linear growth of serialization and byte writes. See completed_spans.proto.
 *
 * The session span should not be written here. Callers are responsible for filtering it out.
 */
class CompletedSpansWriter(
    private val sessionsDir: Lazy<File>,
    private val sessionPartDirectorySource: () -> SessionPartDirectory?,
    private val maxSpans: Int,
    private val telemetryService: TelemetryService,
    private val logger: InternalLogger,
) {

    private val lock = Any()

    @Volatile
    private var currentPart: SessionPartDirectory? = null

    @Volatile
    private var spansWritten: Int = 0

    @Volatile
    private var badSpanReported: Boolean = false

    /**
     * Appends the given spans to the log for the active session part. Returns true if every
     * span was appended (excepting if [maxSpans] is exceeded for the session part).
     */
    fun append(spans: List<Span>): Boolean = synchronized(lock) {
        try {
            appendImpl(spans)
        } catch (exc: Throwable) {
            trackFailure(exc)
            false
        }
    }

    private fun appendImpl(spans: List<Span>): Boolean {
        val directory = sessionPartDirectorySource() ?: return false

        if (directory != currentPart) {
            currentPart = directory
            spansWritten = 0
            badSpanReported = false
        }

        val partDir = File(sessionsDir.value, directory.dirName)
        if (!partDir.isDirectory) {
            trackFailure(IOException("Not a session part directory"))
            return false
        }

        val accepted = admit(spans)
        if (accepted.isEmpty()) {
            return true
        }
        return appendRecords(partDir, accepted)
    }

    /**
     * Appends one record per span to the file, opening it once for the whole batch. Returns true if
     * every span was appended.
     *
     * Every span is encoded into a bytearray before writing to the file. This avoids data corruption if
     * serialization fails halfway through.
     */
    private fun appendRecords(partDir: File, spans: List<Span>): Boolean {
        var allAppended = true
        FileOutputStream(File(partDir, COMPLETED_SPANS_FILE_NAME), true).use { stream ->
            spans.forEach { span ->
                if (!appendSpan(span, stream)) {
                    allAppended = false
                }
            }
        }
        return allAppended
    }

    /**
     * Appends one span to the log, returning false if it could not be serialized or written. A span that
     * is not written does not count towards [maxSpans].
     */
    internal fun appendSpan(
        span: Span,
        stream: OutputStream,
    ): Boolean {
        val record = try {
            CompletedSpans.ADAPTER.encode(CompletedSpans(spans = listOf(span.toProto())))
        } catch (exc: Throwable) {
            reportFirstFailure(exc)
            return false
        }
        try {
            stream.write(record)
        } catch (exc: Throwable) {
            reportFirstFailure(exc)
            return false
        }
        spansWritten++
        return true
    }

    private fun reportFirstFailure(exc: Throwable) {
        if (!badSpanReported) {
            badSpanReported = true
            trackFailure(exc)
        }
    }

    /**
     * Returns a collection of spans within the allowed limit.
     */
    private fun admit(spans: List<Span>): List<Span> {
        val remaining = (maxSpans - spansWritten).coerceAtLeast(0)
        if (spans.size <= remaining) {
            return spans
        }
        repeat(spans.size - remaining) {
            telemetryService.trackAppliedLimit(PERSISTED_SPAN_LIMIT_TYPE, AppliedLimitType.DROP)
        }
        return spans.take(remaining)
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.CompletedSpansWriteFail, exc)
    }

    private companion object {
        private const val PERSISTED_SPAN_LIMIT_TYPE = "persisted_span"
    }
}
