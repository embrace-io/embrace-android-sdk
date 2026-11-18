package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import okio.FileSystem
import okio.Path

/**
 * Resolves each [SdkDir] to a directory under [root], following [SdkStorageLayout].
 */
class SdkStorageImpl(
    override val fileSystem: FileSystem,
    override val root: Path,
    private val errorHandler: ErrorHandler,
) : SdkStorage {

    private val dirs: List<Lazy<Path>> = SdkDir.entries.map { dir ->
        lazy {
            path(dir).also {
                errorHandler.guardSdkCode(dir.errorType) { fileSystem.createDirectories(it) }
            }
        }
    }

    override fun dir(dir: SdkDir): Path = dirs[dir.ordinal].value

    override fun path(dir: SdkDir): Path = SdkStorageLayout.path(root, dir)

    override fun deleteTelemetry() {
        errorHandler.guardSdkCodeEach(SdkErrorType.Ignored, SdkDir.entries.filter { it.isTelemetry }.reversed()) {
            fileSystem.deleteRecursively(path(it))
        }
    }

    private val SdkDir.errorType: SdkErrorType
        get() = if (isTelemetry) SdkErrorType.PayloadStorageFail else SdkErrorType.StorageRootFail
}
