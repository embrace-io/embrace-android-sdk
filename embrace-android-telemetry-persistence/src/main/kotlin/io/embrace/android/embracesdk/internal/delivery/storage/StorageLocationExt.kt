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
 * Deletes every [StorageLocation]'s directory, and everything in it, under [rootDir] and under [fallbackDir], where a
 * location goes if it can't be created under [rootDir]. It runs on the calling thread, so the caller chooses the thread.
 */
fun deleteSdkFiles(rootDir: File, fallbackDir: File) {
    StorageLocation.entries.forEach {
        File(rootDir, it.dir).deleteRecursively()
        File(fallbackDir, it.dir).deleteRecursively()
    }
}
