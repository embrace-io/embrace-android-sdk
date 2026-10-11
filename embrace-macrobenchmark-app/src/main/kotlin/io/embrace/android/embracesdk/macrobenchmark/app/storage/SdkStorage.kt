package io.embrace.android.embracesdk.macrobenchmark.app.storage

import java.io.File

/**
 * The SDK's files in the app's data directory, [dataDir]. The benchmark app reads and writes them to set up
 * what the SDK starts from and to observe what it did, by names the SDK keeps internal, so every such name
 * the app relies on lives here.
 */
internal class SdkStorage(private val dataDir: File) {

    private val filesDir = File(dataDir, FILES_DIR)

    /**
     * Where multi-file mode keeps session parts: StorageLocation.SESSION_SPLIT.
     */
    val sessionsDir: File = File(filesDir, SESSIONS_DIR)

    /**
     * The cache of incomplete payloads, which the SDK may pass resurrected parts through on their way to
     * being stored: StorageLocation.CACHE.
     */
    val cacheDir: File = File(filesDir, CACHE_DIR)

    /**
     * The directory names of the session parts in [sessionsDir].
     */
    fun sessionPartDirs(): List<String> = sessionsDir.list().orEmpty().sorted()

    /**
     * Writes [json] as the remote config response the SDK reads at startup (RemoteConfigStoreImpl), and
     * deletes the binary config cache, which would otherwise take priority over it.
     */
    fun writeRemoteConfig(json: String) {
        val dir = File(filesDir, REMOTE_CONFIG_DIR).apply { mkdirs() }
        File(dir, CACHED_CONFIG_FILE).delete()
        File(dir, REMOTE_CONFIG_RESPONSE_FILE).writeText(json)
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
        const val SESSIONS_DIR = "embrace_sessions_split"
        const val CACHE_DIR = "embrace_cache"
        const val REMOTE_CONFIG_DIR = "embrace_remote_config"
        const val CACHED_CONFIG_FILE = "cached_config"
        const val REMOTE_CONFIG_RESPONSE_FILE = "most_recent_response"

        // the directories under an app's data directory that the app, and so the SDK, writes to
        val SDK_WRITABLE_DIRS = listOf(FILES_DIR, "cache", "shared_prefs", "databases", "no_backup")
    }
}
