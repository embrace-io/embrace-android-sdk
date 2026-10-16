package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.io.File
import java.io.IOException

/**
 * Reconstructs the telemetry persisted in a session part directory into an envelope that can be
 * delivered.
 */
class SessionReconstructionService(
    private val sessionsDir: Lazy<File>,
    private val logger: InternalLogger,
) {

    private val decoder = SessionPartDecoder(logger)

    /**
     * Reconstructs the envelope for the given session part, along with when its files were last
     * written, or null if it cannot be read.
     */
    fun reconstruct(directory: SessionPartDirectory): DecodedSessionPart? =
        SystemTrace.trace("mf-session-reconstruct") {
            try {
                val partDir = File(sessionsDir.value, directory.dirName)
                if (!partDir.isDirectory) {
                    trackFailure(IOException(MISSING_PART_DIR_MSG))
                    return@trace null
                }
                decoder.decode(FileSessionPartSource(partDir))
            } catch (exc: Throwable) {
                trackFailure(exc)
                null
            }
        }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionReconstructionFail, exc)
    }
}
