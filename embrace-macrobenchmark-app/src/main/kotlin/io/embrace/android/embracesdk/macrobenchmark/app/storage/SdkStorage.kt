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
     * Writes [json] as the remote config response the SDK reads at startup (RemoteConfigStoreImpl), and
     * deletes the binary config cache, which would otherwise take priority over it.
     */
    fun writeRemoteConfig(json: String) {
        val dir = File(filesDir, REMOTE_CONFIG_DIR).apply { mkdirs() }
        File(dir, CACHED_CONFIG_FILE).delete()
        File(dir, REMOTE_CONFIG_RESPONSE_FILE).writeText(json)
    }

    private companion object {
        const val FILES_DIR = "files"
        const val REMOTE_CONFIG_DIR = "embrace_remote_config"
        const val CACHED_CONFIG_FILE = "cached_config"
        const val REMOTE_CONFIG_RESPONSE_FILE = "most_recent_response"
    }
}
