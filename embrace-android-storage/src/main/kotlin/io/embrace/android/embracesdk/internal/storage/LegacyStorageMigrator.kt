package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import okio.FileSystem
import okio.Path

/**
 * Moves the 9.x directories in [filesDir] to their [SdkStorageLayout] paths under [noBackupFilesDir], one directory at
 * a time. Each directory is resolved on first use, as resolving it can touch the disk.
 */
class LegacyStorageMigrator(
    private val fileSystem: FileSystem,
    filesDir: () -> Path,
    private val cacheDir: () -> Path,
    noBackupFilesDir: () -> Path,
    private val errorHandler: ErrorHandler,
) {

    private val filesDir: Path by lazy(filesDir)

    private val layout by lazy {
        SdkStorageImpl(fileSystem, noBackupFilesDir() / SdkStorageLayout.ROOT_DIR_NAME, errorHandler)
    }

    @Volatile
    private var configAbandoned: Boolean = false

    /**
     * Whether a 9.x remote config dir exists for [migrateConfig] to move.
     */
    fun hasLegacyConfig(): Boolean = errorHandler.guardSdkCode(SdkErrorType.Ignored, true) {
        fileSystem.exists(legacyDir(SdkDir.REMOTE_CONFIG))
    }

    /**
     * Moves the remote config. Call before the config is read.
     */
    fun migrateConfig() {
        errorHandler.guardSdkCode(SdkErrorType.StorageMigrationFail) {
            if (fileSystem.exists(legacyDir(SdkDir.REMOTE_CONFIG)) && !configAbandoned) {
                SystemTrace.trace("storage-config-migration") {
                    migrate(SdkDir.REMOTE_CONFIG)
                }
            }
        }
    }

    fun abandonConfig() {
        configAbandoned = true
    }

    /**
     * Whether [moveTelemetry] has anything to do. Checks for the existence of the 9.x dir.
     */
    fun hasLegacyTelemetry(): Boolean = errorHandler.guardSdkCode(SdkErrorType.Ignored, true) {
        // resolves to filesDir/embrace, which every 9.x version uses for its install state
        fileSystem.exists(legacyDir(SdkDir.INSTALL_STATE))
    }

    /**
     * Moves the telemetry, the crash marker and any remote config [migrateConfig] left behind. On a first upgrade
     * launch nothing is at its target yet, so each directory is moved by a single rename.
     */
    fun moveTelemetry() {
        SystemTrace.trace("storage-telemetry-move") {
            val abandoned = configAbandoned
            MIGRATED_DIRS
                .filterNot { abandoned && it == SdkDir.REMOTE_CONFIG }
                .forEach(::migrate)
            migrateCrashMarker()
        }
    }

    /**
     * Deletes everything 9.x left behind, including anything [moveTelemetry] failed to move, which is lost rather than
     * retried. Safe to call on every launch.
     */
    fun deleteLegacyInstallState() {
        SystemTrace.trace("storage-legacy-install-state-delete") {
            delete(legacyDirs(LEGACY_DIR_NAMES.keys) + legacyCacheEntries(LEGACY_CACHE_DIR_ENTRIES))
        }
    }

    /**
     * Deletes the 9.x telemetry without moving it.
     */
    fun deleteLegacyTelemetry() {
        delete(legacyDirs(LEGACY_TELEMETRY_DIRS) + legacyCacheEntries(emptyList()))
    }

    /**
     * Moves the 9.x crash marker unless the SDK already wrote one, which is then the more recent crash.
     */
    private fun migrateCrashMarker() = errorHandler.guardSdkCode(SdkErrorType.StorageMigrationFail) {
        merge(
            legacyDir(SdkDir.INSTALL_STATE) / LEGACY_CRASH_MARKER_FILE_NAME,
            layout.path(SdkDir.INSTALL_STATE) / SdkStorageLayout.CRASH_MARKER_FILE_NAME,
        )
    }

    private fun migrate(dir: SdkDir) {
        val source = legacyDir(dir)
        val merged = errorHandler.guardSdkCode(SdkErrorType.StorageMigrationFail, false) {
            merge(source, layout.path(dir))
        }
        if (merged) {
            delete(listOf(source))
        }
    }

    /**
     * Moves [source] to [target], recursing into directories present on both sides. An entry already at its target is
     * left where it is. Returns false if anything failed to move.
     */
    private fun merge(source: Path, target: Path): Boolean {
        val sourceMetadata = fileSystem.metadataOrNull(source) ?: return true
        val targetMetadata = fileSystem.metadataOrNull(target)
        return when {
            targetMetadata == null -> {
                fileSystem.createDirectories(checkNotNull(target.parent))
                fileSystem.atomicMove(source, target)
                true
            }

            sourceMetadata.isDirectory && targetMetadata.isDirectory -> fileSystem.list(source).map { child ->
                errorHandler.guardSdkCode(SdkErrorType.StorageMigrationFail, false) {
                    merge(child, target / child.name)
                }
            }.all { it }

            else -> true
        }
    }

    private fun legacyDir(dir: SdkDir): Path = filesDir / LEGACY_DIR_NAMES.getValue(dir)

    private fun legacyDirs(dirs: Collection<SdkDir>): List<Path> =
        errorHandler.guardSdkCode(SdkErrorType.Ignored, emptyList()) { dirs.map(::legacyDir) }

    private fun legacyCacheEntries(extra: List<String>): List<Path> {
        val cacheRoot = errorHandler.guardSdkCode(SdkErrorType.Ignored, null) { cacheDir() } ?: return emptyList()
        return (LEGACY_TELEMETRY_DIRS.map(LEGACY_DIR_NAMES::getValue) + extra).map { cacheRoot / it }
    }

    private fun delete(paths: List<Path>) {
        errorHandler.guardSdkCodeEach(SdkErrorType.Ignored, paths) {
            fileSystem.deleteRecursively(it)
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
        private val LEGACY_TELEMETRY_DIRS = LEGACY_DIR_NAMES.keys.filter { it.isTelemetry }
        private val MIGRATED_DIRS = LEGACY_DIR_NAMES.keys - SdkDir.INSTALL_STATE
        private const val LEGACY_CRASH_MARKER_FILE_NAME = "embrace_crash_marker"
        private val LEGACY_CACHE_DIR_ENTRIES = listOf("emb_config_cache", LEGACY_CRASH_MARKER_FILE_NAME)
    }
}
