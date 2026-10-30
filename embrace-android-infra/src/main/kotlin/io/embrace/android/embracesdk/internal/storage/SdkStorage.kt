package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import java.io.File
import java.io.IOException

/**
 * Resolves each [SdkDir] to a directory under [root].
 */
class SdkStorage(val root: File) {

    private val dirs: List<Lazy<File>> = SdkDir.entries.map { dir ->
        lazy { path(dir).apply(File::mkdirs) }
    }

    /**
     * Returns the directory for [dir], creating it on first access.
     */
    fun dir(dir: SdkDir): File = dirs[dir.ordinal].value

    /**
     * Synchronously deletes all persisted telemetry. Nothing else the SDK stores is deleted, e.g. remote config.
     */
    fun deleteTelemetry() {
        path(SdkDir.TELEMETRY).deleteRecursively()
    }

    private fun path(dir: SdkDir): File = File(dir.parent?.let(::path) ?: root, dir.dirName)
}

/**
 * Creates [SdkStorage] rooted in [noBackupFilesDir]. If the root can't be created this tracks an error and still
 * returns storage at that path, as there is nowhere else the SDK may store data.
 */
fun createSdkStorage(noBackupFilesDir: File, errorHandler: ErrorHandler): SdkStorage {
    val root = File(noBackupFilesDir, SdkStorageLayout.ROOT_DIR_NAME)
    errorHandler.guardSdkCode(SdkErrorType.StorageRootFail) {
        if (!root.mkdirs() && !root.isDirectory) {
            throw IOException("Failed to create storage root")
        }
    }
    return SdkStorage(root)
}
