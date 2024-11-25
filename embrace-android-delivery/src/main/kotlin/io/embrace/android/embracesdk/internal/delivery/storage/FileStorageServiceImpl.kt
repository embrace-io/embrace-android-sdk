package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.io.File
import java.io.InputStream

internal class FileStorageServiceImpl(
    outputDir: Lazy<File>,
    private val logger: EmbLogger,
) : FileStorageService {

    private val payloadDir by outputDir

    override fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream? {
        return try {
            metadata.asFile().inputStream().buffered()
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.PAYLOAD_STORAGE_FAIL, exc)
            null
        }
    }

    private fun StoredTelemetryMetadata.asFile(): File = File(payloadDir, filename)
}
