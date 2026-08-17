package io.embrace.android.embracesdk.internal.session.persistence

import com.squareup.wire.ProtoAdapter
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

        val manifest = readPartFile(
            partDir,
            MANIFEST_FILE_NAME,
            SessionManifest.ADAPTER,
            SessionManifest::format_version,
        ) ?: return null

        val resource = manifest.resource
        if (resource == null) {
            trackFailure(IOException("Manifest has no resource: ${partDir.path}"))
            return null
        }

        val metadata = readPartFile(
            partDir,
            METADATA_FILE_NAME,
            EnvelopeMetadataProto.ADAPTER,
            EnvelopeMetadataProto::format_version,
        )?.toPayload() ?: return null

        // spans & span snapshots not persisted/deserialized yet
        return Envelope(
            resource = resource.toPayload(),
            metadata = metadata,
            version = manifest.envelope_version,
            type = manifest.envelope_type,
            data = SessionPartPayload(
                spans = null,
                spanSnapshots = null,
                sharedLibSymbolMapping = manifest.shared_lib_symbol_mapping?.symbols,
            ),
        )
    }

    /**
     * Decodes [fileName] from a session part directory, or null if it is absent, cannot be read,
     * or was written by an SDK using a different on-disk layout.
     *
     * [formatVersion] reads the version stamped on the decoded message. Every persisted file
     * carries one, so a file holding no data at all decodes to version 0 and is rejected rather
     * than mistaken for a message whose fields were all left at their defaults.
     */
    private fun <T> readPartFile(
        partDir: File,
        fileName: String,
        adapter: ProtoAdapter<T>,
        formatVersion: (T) -> Int,
    ): T? {
        val src = File(partDir, fileName)
        return try {
            if (!src.isFile) {
                throw IOException("File not found: ${src.path}")
            }
            val message = src.inputStream().buffered().use(adapter::decode)

            val version = formatVersion(message)
            if (version != FORMAT_VERSION) {
                throw IOException("Unsupported format version in ${src.path}: $version")
            }
            message
        } catch (exc: Throwable) {
            trackFailure(exc)
            null
        }
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionReconstructionFail, exc)
    }
}
