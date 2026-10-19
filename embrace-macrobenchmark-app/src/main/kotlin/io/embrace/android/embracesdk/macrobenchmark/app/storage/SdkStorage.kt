package io.embrace.android.embracesdk.macrobenchmark.app.storage

import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import java.io.File

/**
 * The SDK's files in the app's data directory, [dataDir]. The benchmark app reads and writes them to set up
 * what the SDK starts from and to observe what it did, by names defined in respective modules, so every such
 * location is represented and managed by this.
 */
internal class SdkStorage(private val dataDir: File) {

    private val filesDir = File(dataDir, FILES_DIR)

    /**
     * Where multi-file mode keeps session parts: [StorageLocation.SESSION_SPLIT].
     */
    val sessionsDir: File = File(filesDir, StorageLocation.SESSION_SPLIT.dir)

    /**
     * The cache of incomplete payloads, which the SDK may pass resurrected parts through on their way to
     * being stored: [StorageLocation.CACHE].
     */
    val cacheDir: File = File(filesDir, StorageLocation.CACHE.dir)

    /**
     * Where the SDK persists the remote config: [PersistedConfig.STORAGE_DIR_NAME].
     */
    val remoteConfigDir: File = File(filesDir, PersistedConfig.STORAGE_DIR_NAME)

    /**
     * The directory names of the session parts in [sessionsDir].
     */
    fun sessionPartDirs(): List<String> = sessionsDir.list().orEmpty().sorted()

    /**
     * Writes [json] as the remote config response the SDK reads at startup (RemoteConfigStoreImpl), and
     * deletes the binary config cache, which would otherwise take priority over it.
     */
    fun writeRemoteConfig(json: String) {
        remoteConfigDir.mkdirs()
        File(remoteConfigDir, CACHED_CONFIG_FILE).delete()
        File(remoteConfigDir, REMOTE_CONFIG_RESPONSE_FILE).writeText(json)
    }

    /**
     * Deletes everything the SDK can have written, as `pm clear` would: the app's files, cache, preferences,
     * databases and files kept out of backups. The app's code, and any directory of the benchmark's own
     * beside these, are left alone.
     */
    fun clear() {
        SDK_WRITABLE_DIRS.forEach { File(dataDir, it).deleteRecursively() }
    }

    private companion object {
        const val FILES_DIR = "files"
        const val CACHED_CONFIG_FILE = "cached_config"
        const val REMOTE_CONFIG_RESPONSE_FILE = "most_recent_response"

        // the directories under an app's data directory that the app, and so the SDK, writes to
        val SDK_WRITABLE_DIRS = listOf(FILES_DIR, "cache", "shared_prefs", "databases", "no_backup")
    }
}
