package io.embrace.android.embracesdk.macrobenchmark.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import io.embrace.android.embracesdk.benchmark.scenario.HostTask

/**
 * The activity a benchmark launches for the app to run a [HostTask], which its intent carries. The process's
 * [ScenarioHost] runs it, and this activity displays its status, which is the only channel back to the
 * benchmark driving it.
 */
class ScenarioHostActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this)
        setContentView(status)
    }

    /**
     * Shows [text] as the status. Call it on the UI thread.
     */
    fun showStatus(text: String) {
        status.text = text
    }
}
