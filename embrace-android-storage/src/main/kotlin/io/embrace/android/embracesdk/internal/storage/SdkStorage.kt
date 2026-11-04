package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import okio.FileSystem
import okio.Path

/**
 * Resolves each [SdkDir] to a directory the SDK can write to.
 */
interface SdkStorage {

    /**
     * The file system that every directory returned by [dir] belongs to.
     */
    val fileSystem: FileSystem

    /**
     * Returns the directory for [dir].
     */
    fun dir(dir: SdkDir): Path

    /**
     * Returns where [dir] is without creating it.
     */
    fun path(dir: SdkDir): Path

    /**
     * Synchronously deletes all persisted telemetry.
     */
    fun deleteTelemetry()
}

/**
 * Creates [SdkStorageImpl] under [noBackupFilesDir]. If the root can't be created this tracks an error and
 * still returns storage at that path.
 */
fun createSdkStorage(fileSystem: FileSystem, noBackupFilesDir: Path, errorHandler: ErrorHandler): SdkStorageImpl {
    val root = noBackupFilesDir / SdkStorageLayout.ROOT_DIR_NAME
    errorHandler.guardSdkCode(SdkErrorType.StorageRootFail) {
        fileSystem.createDirectories(root)
    }
    return SdkStorageImpl(fileSystem, root, errorHandler)
}

/**
 * Whether this directory is, or is in, [SdkDir.TELEMETRY].
 */
internal val SdkDir.isTelemetry: Boolean
    get() = generateSequence(this, SdkDir::parent).any { it == SdkDir.TELEMETRY }
