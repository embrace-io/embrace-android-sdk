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

    private companion object {
        const val FORMAT_VERSION = 1
        const val MANIFEST_FILE_NAME = "manifest.pb"
    }

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
            trackFailure(IOException("Not a session part directory: ${partDir.path}"))
            return false
        }

        val dst = File(partDir, MANIFEST_FILE_NAME)
        if (dst.exists()) {
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

        // write to a temporary file then rename it, so partial manifests aren't observed
        val tmpFile = File.createTempFile(MANIFEST_FILE_NAME, ".tmp", partDir)
        try {
            tmpFile.outputStream().buffered().use { stream ->
                SessionManifest.ADAPTER.encode(stream, manifest)
            }
            if (tmpFile.renameTo(dst)) {
                return true
            }
            trackFailure(IOException("Failed to rename manifest"))
            return false
        } finally {
            tmpFile.delete()
        }
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionManifestWriteFail, exc)
    }
}
