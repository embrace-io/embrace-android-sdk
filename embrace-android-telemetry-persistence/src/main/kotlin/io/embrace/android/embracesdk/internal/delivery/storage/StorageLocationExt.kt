package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import java.io.File

/**
 * Get the directory as a [File] object
 */
fun StorageLocation.asFile(
    errorHandler: ErrorHandler?,
    rootDirSupplier: () -> File,
    fallbackDirSupplier: () -> File,
): Lazy<File> = lazy {
    try {
        File(rootDirSupplier(), dir).apply(File::mkdirs)
    } catch (exc: Throwable) {
        errorHandler?.trackSdkError(SdkErrorType.PayloadStorageFail, exc)
        File(fallbackDirSupplier(), dir).apply(File::mkdirs)
    }
}

/**
 * Synchronously deletes the telemetry the SDK persisted: every [StorageLocation]'s directory, and everything in it,
 * under [rootDir] and [fallbackDir]. Nothing else the SDK stores is deleted, e.g. remote config.
 */
fun deletePersistedTelemetry(rootDir: File, fallbackDir: File) {
    StorageLocation.entries.forEach {
        File(rootDir, it.dir).deleteRecursively()
        File(fallbackDir, it.dir).deleteRecursively()
    }
}
