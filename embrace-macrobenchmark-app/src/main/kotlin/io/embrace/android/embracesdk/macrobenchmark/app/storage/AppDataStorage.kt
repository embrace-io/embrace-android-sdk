package io.embrace.android.embracesdk.macrobenchmark.app.storage

import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.storage.SdkDir
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import okio.FileSystem
import okio.Path

/**
 * The SDK's files in the app's data directory, [dataDir]. The benchmark app reads and writes them to set up
 * what the SDK starts from and to observe what it did, by names defined in respective modules, so every such
 * location is represented and managed by this.
 */
internal class AppDataStorage(
    private val fileSystem: FileSystem,
    private val dataDir: Path,
) {

    private val sdkRoot = dataDir / NO_BACKUP_DIR / SdkStorageLayout.ROOT_DIR_NAME

    /**
     * Where multi-file mode keeps session parts: [SdkDir.TELEMETRY_PENDING_SESSION_PARTS].
     */
    val sessionsDir: Path = sdkDir(SdkDir.TELEMETRY_PENDING_SESSION_PARTS)

    /**
     * The cache of incomplete payloads, which the SDK may pass resurrected parts through on their way to
     * being stored: [SdkDir.TELEMETRY_PENDING_SNAPSHOTS].
     */
    val cacheDir: Path = sdkDir(SdkDir.TELEMETRY_PENDING_SNAPSHOTS)

    /**
     * Where the SDK persists the remote config: [SdkDir.REMOTE_CONFIG].
     */
    val remoteConfigDir: Path = sdkDir(SdkDir.REMOTE_CONFIG)

    private fun sdkDir(dir: SdkDir): Path = SdkStorageLayout.path(sdkRoot, dir)

    /**
     * The directory names of the session parts in [sessionsDir].
     */
    fun sessionPartDirs(): List<String> = list(sessionsDir).map(Path::name).sorted()

    /**
     * How many payloads are in [cacheDir].
     */
    fun cachedPayloadCount(): Int = list(cacheDir).size

    /**
     * Whether the session part named [name] is in [sessionsDir].
     */
    fun hasSessionPart(name: String): Boolean = fileSystem.exists(sessionsDir / name)

    /**
     * Writes [json] as the remote config response the SDK reads at startup (RemoteConfigStoreImpl), and
     * deletes the binary config cache, which would otherwise take priority over it.
     */
    fun writeRemoteConfig(json: String) {
        fileSystem.createDirectories(remoteConfigDir)
        fileSystem.delete(remoteConfigDir / PersistedConfig.CACHED_CONFIG_FILE_NAME)
        fileSystem.write(remoteConfigDir / PersistedConfig.CONFIG_RESPONSE_FILE_NAME) { writeUtf8(json) }
    }

    /**
     * Deletes everything the SDK can have written, as `pm clear` would: the app's files, cache, preferences,
     * databases and files kept out of backups. The app's code, and any directory of the benchmark's own
     * beside these, are left alone.
     */
    fun clear() {
        SDK_WRITABLE_DIRS.forEach { fileSystem.deleteRecursively(dataDir / it) }
    }

    private fun list(dir: Path): List<Path> = fileSystem.listOrNull(dir).orEmpty()

    private companion object {
        const val NO_BACKUP_DIR = "no_backup"

        // the directories under an app's data directory that a clear() will remove
        // TODO: keep SDK-written files other than the session parts data as part of the restorable fixtures
        //       to more closely simulate the state of the SDK files in an install which has a dead session to resurrect.
        val SDK_WRITABLE_DIRS = listOf("files", "cache", "shared_prefs", "databases", NO_BACKUP_DIR)
    }
}
