package io.embrace.android.embracesdk.macrobenchmark.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Launcher activity that draws a single frame as cheaply as possible, so that the startup timings
 * reflect SDK init rather than the app's own UI work.
 */
class BenchmarkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Embrace Macrobenchmark" })
    }
}
