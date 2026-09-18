package io.embrace.android.embracesdk.macrobenchmark.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.EXTRA_SCENARIO_ID

/**
 * Runs the scenario named by [EXTRA_SCENARIO_ID] and displays its status, which is the only channel
 * back to the benchmark driving it.
 */
class ScenarioActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this)
        setContentView(status)
    }

    override fun onResume() {
        super.onResume()
        val scenarioId = checkNotNull(intent.getStringExtra(EXTRA_SCENARIO_ID)) {
            "launch this activity with a $EXTRA_SCENARIO_ID extra naming the scenario to run"
        }
        ScenarioRunner.onForeground(scenarioId) { text ->
            runOnUiThread { status.text = text }
        }
    }
}
