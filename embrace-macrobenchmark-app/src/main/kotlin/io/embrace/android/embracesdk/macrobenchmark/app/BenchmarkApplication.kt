package io.embrace.android.embracesdk.macrobenchmark.app

import android.app.Application
import android.provider.Settings
import io.embrace.android.embracesdk.Embrace
import java.io.File

/**
 * Starts the SDK on the main thread during app startup.
 */
class BenchmarkApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        seedRemoteConfig()
        Embrace.start(this)
    }

    /**
     * Selects the persistence layer without rebuilding the APK, from a global setting that survives
     * the `pm clear` the benchmark runs between iterations:
     *
     * ```
     * adb shell settings put global embrace_pct_multi_file_persistence 100
     * ```
     *
     * This is required as the APK is non-debuggable in typical conditions, and only works as
     * pm clear is executed each run.
     */
    private fun seedRemoteConfig() {
        val pct = Settings.Global.getString(contentResolver, "embrace_pct_multi_file_persistence") ?: return
        val dir = File(filesDir, "embrace_remote_config").apply { mkdirs() }
        File(dir, "most_recent_response").writeText("""{"pct_multi_file_persistence_enabled":$pct}""")
    }
}
