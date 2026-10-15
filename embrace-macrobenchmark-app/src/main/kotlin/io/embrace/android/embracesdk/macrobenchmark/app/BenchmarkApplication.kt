package io.embrace.android.embracesdk.macrobenchmark.app

import android.app.Application
import android.provider.Settings
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.MULTI_FILE_PERSISTENCE_SETTING
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.OTEL_KOTLIN_SDK_SETTING
import io.embrace.android.embracesdk.macrobenchmark.app.storage.SdkStorage
import java.io.File

/**
 * Starts the SDK on the main thread during app startup.
 */
class BenchmarkApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        seedRemoteConfig(SdkStorage(File(applicationInfo.dataDir)))
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
     * This is required as the APK is non-debuggable in typical conditions.
     */
    private fun seedRemoteConfig(storage: SdkStorage) {
        val fields = listOfNotNull(
            globalSetting(MULTI_FILE_PERSISTENCE_SETTING)?.let { "\"pct_multi_file_persistence_enabled\":$it" },
            globalSetting(OTEL_KOTLIN_SDK_SETTING)?.let { "\"otel_kotlin_sdk\":{\"pct_enabled\":$it}" },
        )
        if (fields.isEmpty()) {
            return
        }
        storage.writeRemoteConfig(fields.joinToString(",", "{", "}"))
    }

    private fun globalSetting(name: String): String? = Settings.Global.getString(contentResolver, name)
}
