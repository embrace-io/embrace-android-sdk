package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.io.File
import java.io.IOException

/**
 * Reconstructs the telemetry persisted in a session part directory into an envelope that can be
 * delivered.
 */
class SessionReconstructionService(
    private val sessionsDir: Lazy<File>,
    private val errorHandler: ErrorHandler,
) {

    private val decoder = SessionPartDecoder(errorHandler)

    /**
     * Reconstructs the envelope for the given session part, along with when its files were last
     * written, or null if it cannot be read.
     */
    fun reconstruct(directory: SessionPartDirectory): DecodedSessionPart? =
        SystemTrace.trace("mf-session-reconstruct") {
            errorHandler.guardSdkCode(SdkErrorType.SessionReconstructionFail, null) {
                val partDir = File(sessionsDir.value, directory.dirName)
                if (!partDir.isDirectory) {
                    errorHandler.trackSdkError(SdkErrorType.SessionReconstructionFail, IOException(MISSING_PART_DIR_MSG))
                    return@guardSdkCode null
                }
                decoder.decode(FileSessionPartSource(partDir))
            }
        }
}
