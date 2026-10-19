package io.embrace.android.embracesdk.macrobenchmark.app

import android.app.Application
import io.embrace.android.embracesdk.Embrace

/**
 * The app the macrobenchmark measures. [ScenarioHost] does everything a benchmark needs of the app, so all the
 * app does is hand the host the way it starts the SDK under test, as any other benchmarked app would.
 */
class BenchmarkApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ScenarioHost(this).start { Embrace.start(this) }
    }
}
