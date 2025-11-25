package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.io.File

/**
 * Get the directory as a [File] object
 */
fun StorageLocation.asFile(
    logger: EmbLogger,
    rootDirSupplier: () -> File,
    fallbackDirSupplier: () -> File,
): Lazy<File> = lazy {
    try {
        File(rootDirSupplier(), dir).apply(File::mkdirs)
    } catch (exc: Throwable) {
        logger.trackInternalError(InternalErrorType.PAYLOAD_STORAGE_FAIL, exc)
        File(fallbackDirSupplier(), dir).apply(File::mkdirs)
    }
}
