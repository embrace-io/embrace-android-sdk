package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Span
import java.io.File
import java.io.IOException

/**
 * Writes the session span for a session part to its directory.
 */
class SessionSpanWriter(
    private val sessionsDir: Lazy<File>,
    private val sessionPartDirectorySource: () -> SessionPartDirectory?,
    private val logger: InternalLogger,
) {

    private val lock = Any()

    fun write(span: Span): Boolean = synchronized(lock) {
        try {
            writeImpl(span)
        } catch (exc: Throwable) {
            trackFailure(exc)
            false
        }
    }

    private fun writeImpl(span: Span): Boolean {
        val directory = sessionPartDirectorySource() ?: return false

        val partDir = File(sessionsDir.value, directory.dirName)
        if (!partDir.isDirectory) {
            trackFailure(IOException("Not a session part directory: ${partDir.path}"))
            return false
        }
        val sessionSpan = SessionSpan(
            format_version = FORMAT_VERSION,
            span = span.toProto(),
        )
        writeAtomically(partDir, SESSION_SPAN_FILE_NAME) { stream ->
            SessionSpan.ADAPTER.encode(stream, sessionSpan)
        }
        return true
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionSpanWriteFail, exc)
    }
}
