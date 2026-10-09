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
     * Selects the persistence mode and the opentelemetry-kotlin implementation without rebuilding
     * the APK, from global settings that survive the `pm clear` the benchmark runs between iterations:
     *
     * ```
     * adb shell settings put global embrace_pct_multi_file_persistence 100
     * adb shell settings put global embrace_pct_otel_kotlin_sdk 100
     * ```
     *
     * This is required as the APK is non-debuggable in typical conditions. The binary config cache
     * takes priority over the JSON response, so it is deleted for the seeded config to be read.
     */
    private fun seedRemoteConfig() {
        val fields = listOfNotNull(
            globalSetting("embrace_pct_multi_file_persistence")?.let { "\"pct_multi_file_persistence_enabled\":$it" },
            globalSetting("embrace_pct_otel_kotlin_sdk")?.let { "\"otel_kotlin_sdk\":{\"pct_enabled\":$it}" },
        )
        if (fields.isEmpty()) {
            return
        }
        val dir = File(filesDir, "embrace_remote_config").apply { mkdirs() }
        File(dir, "cached_config").delete()
        File(dir, "most_recent_response").writeText(fields.joinToString(",", "{", "}"))
    }

    private fun globalSetting(name: String): String? = Settings.Global.getString(contentResolver, name)
}
