package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import io.embrace.android.embracesdk.internal.storage.LegacySdkStorage.Companion.LEGACY_DIR_NAMES
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import okio.FileSystem
import okio.Path

/**
 * Moves legacy directories in [filesDir] to their [SdkStorageLayout] paths under [noBackupFilesDir]. Files
 * are staged in [SdkStorageLayout.MIGRATION_DIR_NAME] so interrupted runs resume on the next launch.
 */
class LegacyStorageMigrator(
    private val fileSystem: FileSystem,
    private val filesDir: Path,
    private val cacheDir: () -> Path,
    noBackupFilesDir: Path,
    private val errorHandler: ErrorHandler,
) {

    private val root = noBackupFilesDir / SdkStorageLayout.ROOT_DIR_NAME
    private val staging = noBackupFilesDir / SdkStorageLayout.MIGRATION_DIR_NAME

    /**
     * Returns true if this call committed a migration.
     */
    fun migrate(): Boolean = errorHandler.guardSdkCode(SdkErrorType.StorageMigrationFail, false) {
        if (fileSystem.exists(root) || !hasLegacyData()) {
            return@guardSdkCode false
        }
        SystemTrace.trace("storage-migration") {
            fileSystem.createDirectories(staging)
            val layout = SdkStorageImpl(fileSystem, staging, errorHandler)
            errorHandler.guardSdkCodeEach(SdkErrorType.StorageMigrationFail, LEGACY_DIR_NAMES.entries) {
                stage(filesDir / it.value, layout.path(it.key))
            }
            stripLegacyPrefixes(layout.path(SdkDir.INSTALL_STATE))
            fileSystem.atomicMove(staging, root)
            true
        }
    }

    /**
     * Deletes legacy dirs that weren't migrated and the legacy cache dir entries. Should only be called
     * after [migrate] returned true.
     */
    fun deleteLeftovers() {
        val cacheRoot = errorHandler.guardSdkCode(SdkErrorType.Ignored, null) { cacheDir() }
        val cacheEntries = LEGACY_DIR_NAMES.filterKeys { it.isTelemetry }.values + LEGACY_CACHE_DIR_ENTRIES
        val leftovers = LEGACY_DIR_NAMES.values.map { filesDir / it } +
            listOfNotNull(cacheRoot).flatMap { dir -> cacheEntries.map { dir / it } }
        errorHandler.guardSdkCodeEach(SdkErrorType.Ignored, leftovers) {
            fileSystem.deleteRecursively(it)
        }
    }

    private fun hasLegacyData(): Boolean =
        fileSystem.exists(staging) || LEGACY_DIR_NAMES.values.any { fileSystem.exists(filesDir / it) }

    private fun stage(source: Path, target: Path) {
        val sourceMetadata = fileSystem.metadataOrNull(source) ?: return
        val targetMetadata = fileSystem.metadataOrNull(target)
        if (targetMetadata == null) {
            fileSystem.createDirectories(checkNotNull(target.parent))
            fileSystem.atomicMove(source, target)
        } else if (sourceMetadata.isDirectory && targetMetadata.isDirectory) {
            errorHandler.guardSdkCodeEach(SdkErrorType.StorageMigrationFail, fileSystem.list(source)) {
                stage(it, target / it.name)
            }
        }
    }

    private fun stripLegacyPrefixes(dir: Path) {
        if (!fileSystem.exists(dir)) {
            return
        }
        errorHandler.guardSdkCodeEach(SdkErrorType.StorageMigrationFail, fileSystem.list(dir)) { child ->
            val prefix = LEGACY_PREFIXES.firstOrNull { child.name.startsWith(it) } ?: return@guardSdkCodeEach
            val target = dir / child.name.removePrefix(prefix)
            if (!fileSystem.exists(target)) {
                fileSystem.atomicMove(child, target)
            }
        }
    }

    private companion object {
        val LEGACY_PREFIXES = listOf("embrace_", "emb_")
        val LEGACY_CACHE_DIR_ENTRIES = LegacySdkStorage.ORPHANED_CACHE_DIR_ENTRIES + "embrace_crash_marker"
    }
}
