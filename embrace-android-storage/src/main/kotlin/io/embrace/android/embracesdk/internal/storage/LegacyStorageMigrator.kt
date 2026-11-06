package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
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
            stageLegacyDirs()
            fileSystem.atomicMove(staging, root)
            true
        }
    }

    /**
     * Merges anything a failed or interrupted [migrate] left behind into the live root, without overwriting what is
     * already there, then deletes whatever couldn't be merged along with the legacy cache dir entries. Does nothing
     * to legacy data if the live root doesn't exist.
     */
    fun collectLeftovers() {
        val merged = errorHandler.guardSdkCode(SdkErrorType.StorageMigrationFail, false) {
            if (!fileSystem.exists(root)) {
                return@guardSdkCode false
            }
            if (hasLegacyData()) {
                stageLegacyDirs()
                stage(staging, root)
            }
            true
        }
        if (!merged) {
            return
        }
        val cacheRoot = errorHandler.guardSdkCode(SdkErrorType.Ignored, null) { cacheDir() }
        val cacheEntries = LEGACY_DIR_NAMES.filterKeys { it.isTelemetry }.values + LEGACY_CACHE_DIR_ENTRIES
        val leftovers = listOf(staging) + LEGACY_DIR_NAMES.values.map { filesDir / it } +
            listOfNotNull(cacheRoot).flatMap { dir -> cacheEntries.map { dir / it } }
        errorHandler.guardSdkCodeEach(SdkErrorType.Ignored, leftovers) {
            fileSystem.deleteRecursively(it)
        }
    }

    private fun hasLegacyData(): Boolean =
        fileSystem.exists(staging) || LEGACY_DIR_NAMES.values.any { fileSystem.exists(filesDir / it) }

    private fun stageLegacyDirs() {
        fileSystem.createDirectories(staging)
        val layout = SdkStorageImpl(fileSystem, staging, errorHandler)
        errorHandler.guardSdkCodeEach(SdkErrorType.StorageMigrationFail, LEGACY_DIR_NAMES.entries) {
            stage(filesDir / it.value, layout.path(it.key))
        }
        stripLegacyPrefixes(layout.path(SdkDir.INSTALL_STATE))
    }

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

    companion object {
        val LEGACY_DIR_NAMES: Map<SdkDir, String> = SdkDir.entries.mapNotNull { dir ->
            when (dir) {
                SdkDir.REMOTE_CONFIG -> "embrace_remote_config"
                SdkDir.INSTALL_STATE -> "embrace"
                SdkDir.TELEMETRY, SdkDir.TELEMETRY_PENDING -> null
                SdkDir.TELEMETRY_READY -> "embrace_payloads"
                SdkDir.TELEMETRY_PENDING_SNAPSHOTS -> "embrace_cache"
                SdkDir.TELEMETRY_PENDING_SESSION_PARTS -> "embrace_sessions_split"
                SdkDir.TELEMETRY_PENDING_NATIVE_CRASHES -> "embrace_native"
                SdkDir.TELEMETRY_LOG_ENVELOPES -> "embrace_envelopes"
            }?.let { dir to it }
        }.toMap()
        private val LEGACY_PREFIXES = listOf("embrace_", "emb_")
        private val LEGACY_CACHE_DIR_ENTRIES = listOf("emb_config_cache", "embrace_crash_marker")
    }
}
