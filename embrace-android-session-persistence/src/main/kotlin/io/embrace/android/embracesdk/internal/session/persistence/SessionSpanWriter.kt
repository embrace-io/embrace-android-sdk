package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.SystemTrace

/**
 * Writes the session span for a session part to its directory.
 */
class SessionSpanWriter(
    private val target: SessionPartWriteTarget,
    private val logger: InternalLogger,
) {

    fun write(span: Span): Boolean = SystemTrace.trace("mf-write-session-span") {
        try {
            writeImpl(span)
        } catch (exc: Throwable) {
            trackFailure(exc)
            false
        }
    }

    private fun writeImpl(span: Span): Boolean {
        val directory = target.directory ?: return false
        val partDir = target.partDir(directory, ::trackFailure) ?: return false

        val sessionSpan = SessionPartSpan(
            format_version = FORMAT_VERSION,
            span = span.toProto(),
        )
        writeAtomically(partDir, SESSION_SPAN_FILE_NAME, MAX_PART_FILE_BYTES) { stream ->
            SessionPartSpan.ADAPTER.encode(stream, sessionSpan)
        }
        return true
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionSpanWriteFail, exc)
    }
}
