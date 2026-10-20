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
        File(remoteConfigDir, PersistedConfig.CACHED_CONFIG_FILE_NAME).delete()
        File(remoteConfigDir, PersistedConfig.CONFIG_RESPONSE_FILE_NAME).writeText(json)
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

        // the directories under an app's data directory that a clear() will remove
        // TODO: keep SDK-written files other than the session parts data as part of the restorable fixtures
        //       to more closely simulate the state of the SDK files in an install which has a dead session to resurrect.
        val SDK_WRITABLE_DIRS = listOf(FILES_DIR, "cache", "shared_prefs", "databases", "no_backup")
    }
}
