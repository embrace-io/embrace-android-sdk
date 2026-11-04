package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import okio.FileSystem
import okio.Path

/**
 * Resolves each [SdkDir] to a directory directly in [filesDir] named in [LEGACY_DIR_NAMES].
 * If creating it fails for any reason, telemetry falls back to the same name in [cacheDir] and
 * install state falls back to [cacheDir] itself. Remote config has no fallback.
 *
 * [SdkDir.TELEMETRY] and [SdkDir.TELEMETRY_PENDING] have no 9.x directory, as 9.x telemetry
 * dirs sit directly in [filesDir]. Asking for either tracks an error and returns [filesDir].
 */
class LegacySdkStorage(
    override val fileSystem: FileSystem,
    private val filesDir: Path,
    private val cacheDir: () -> Path,
    private val errorHandler: ErrorHandler,
) : SdkStorage {

    private val dirs: List<Lazy<Path>> = SdkDir.entries.map { dir ->
        lazy {
            errorHandler.guardSdkCode(SdkErrorType.PayloadStorageFail, filesDir) {
                resolve(dir)
            }
        }
    }

    override fun dir(dir: SdkDir): Path = dirs[dir.ordinal].value

    override fun path(dir: SdkDir): Path = dirs[dir.ordinal].let {
        if (it.isInitialized()) {
            it.value
        } else {
            errorHandler.guardSdkCode(SdkErrorType.PayloadStorageFail, filesDir) { primary(dir) }
        }
    }

    override fun deleteTelemetry() {
        val cacheRoot = errorHandler.guardSdkCode(SdkErrorType.Ignored, null) { cacheDir() }
        val telemetryDirs = listOfNotNull(filesDir, cacheRoot).flatMap { root ->
            LEGACY_DIR_NAMES.filterKeys { it.isTelemetry }.values.map { root / it }
        }
        errorHandler.guardSdkCodeEach(SdkErrorType.Ignored, telemetryDirs) {
            fileSystem.deleteRecursively(it)
        }
    }

    private fun primary(dir: SdkDir): Path = filesDir / LEGACY_DIR_NAMES.getValue(dir)

    private fun resolve(dir: SdkDir): Path {
        val primary = primary(dir)
        val fallback = when {
            dir.isTelemetry -> { -> cacheDir() / primary.name }
            dir == SdkDir.INSTALL_STATE -> cacheDir
            else -> return primary.also(::createUntracked)
        }
        val errorType = if (dir.isTelemetry) SdkErrorType.PayloadStorageFail else SdkErrorType.Ignored
        return errorHandler.guardSdkCode(errorType, null) {
            primary.also(fileSystem::createDirectories)
        } ?: errorHandler.guardSdkCode(errorType, primary) {
            fallback().also(::createUntracked)
        }
    }

    private fun createUntracked(path: Path) {
        errorHandler.guardSdkCode(SdkErrorType.Ignored) { fileSystem.createDirectories(path) }
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
        val ORPHANED_CACHE_DIR_ENTRIES: List<String> = listOf("emb_config_cache")
    }
}
