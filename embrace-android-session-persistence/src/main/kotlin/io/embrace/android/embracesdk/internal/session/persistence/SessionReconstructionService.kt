package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
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

    /**
     * Reconstructs the envelope for the given session part, or null if it cannot be read.
     */
    fun reconstruct(directory: SessionPartDirectory): Envelope<SessionPartPayload>? {
        return try {
            reconstructImpl(directory)
        } catch (exc: Throwable) {
            trackFailure(exc)
            null
        }
    }

    private fun reconstructImpl(directory: SessionPartDirectory): Envelope<SessionPartPayload>? {
        val partDir = File(sessionsDir.value, directory.dirName)
        if (!partDir.isDirectory) {
            trackFailure(IOException("Not a session part directory: ${partDir.path}"))
            return null
        }

        val src = File(partDir, MANIFEST_FILE_NAME)
        if (!src.isFile) {
            trackFailure(IOException("Manifest not found: ${src.path}"))
            return null
        }
        val manifest = src.inputStream().buffered().use(SessionManifest.ADAPTER::decode)

        if (manifest.format_version != FORMAT_VERSION) {
            trackFailure(IOException("Unsupported manifest format version: ${manifest.format_version}"))
            return null
        }
        val resource = manifest.resource
        if (resource == null) {
            trackFailure(IOException("Manifest has no resource: ${src.path}"))
            return null
        }

        // various properties not persisted/deserialized yet
        return Envelope(
            resource = resource.toPayload(),
            metadata = null,
            version = manifest.envelope_version,
            type = manifest.envelope_type,
            data = SessionPartPayload(
                spans = null,
                spanSnapshots = null,
                sharedLibSymbolMapping = manifest.shared_lib_symbol_mapping?.symbols,
            ),
        )
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionReconstructionFail, exc)
    }
}
