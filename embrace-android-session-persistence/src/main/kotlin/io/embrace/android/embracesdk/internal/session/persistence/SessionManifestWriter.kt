package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import java.io.File
import java.io.IOException

/**
 * Writes the data that is immutable for the lifetime of a session part to its directory.
 */
class SessionManifestWriter(
    private val sessionsDir: Lazy<File>,
    private val logger: InternalLogger,
) {

    /**
     * Writes the manifest for the given session part, unless one already exists.
     *
     * Returns true if a manifest is on disk for this session part
     */
    fun write(
        directory: SessionPartDirectory,
        resource: EnvelopeResource,
        envelopeVersion: String,
        envelopeType: String,
        sharedLibSymbolMapping: Map<String, String>? = null,
    ): Boolean {
        return try {
            writeImpl(directory, resource, envelopeVersion, envelopeType, sharedLibSymbolMapping)
        } catch (exc: Throwable) {
            trackFailure(exc)
            false
        }
    }

    private fun writeImpl(
        directory: SessionPartDirectory,
        resource: EnvelopeResource,
        envelopeVersion: String,
        envelopeType: String,
        sharedLibSymbolMapping: Map<String, String>?,
    ): Boolean {
        val partDir = File(sessionsDir.value, directory.dirName)
        if (!partDir.isDirectory) {
            trackFailure(IOException("Not a session part directory"))
            return false
        }

        if (File(partDir, MANIFEST_FILE_NAME).exists()) {
            return true
        }

        // build the message before touching the filesystem
        val manifest = SessionManifest(
            format_version = FORMAT_VERSION,
            envelope_version = envelopeVersion,
            envelope_type = envelopeType,
            user_session_id = directory.userSessionId,
            session_part_id = directory.sessionPartId,
            shared_lib_symbol_mapping = sharedLibSymbolMapping?.let { symbols ->
                SharedLibSymbolMapping(symbols = symbols)
            },
            resource = resource.toProto(),
        )

        writeAtomically(partDir, MANIFEST_FILE_NAME) { stream ->
            SessionManifest.ADAPTER.encode(stream, manifest)
        }
        return true
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionManifestWriteFail, exc)
    }
}
