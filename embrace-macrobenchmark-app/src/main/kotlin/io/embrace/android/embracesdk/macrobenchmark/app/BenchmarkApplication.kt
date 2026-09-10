package io.embrace.android.embracesdk.macrobenchmark.app

import android.app.Application
import io.embrace.android.embracesdk.Embrace

/**
 * Starts the SDK on the main thread during app startup, which is what SdkInitBenchmark in
 */
class BenchmarkApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Embrace.start(this)
    }
}
