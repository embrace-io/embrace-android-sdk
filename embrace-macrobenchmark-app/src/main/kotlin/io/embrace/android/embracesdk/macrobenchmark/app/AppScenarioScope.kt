package io.embrace.android.embracesdk.macrobenchmark.app

import io.embrace.android.embracesdk.benchmark.scenario.ScenarioScope
import io.embrace.android.embracesdk.internal.api.SdkApi

/**
 * Runs a scenario in the app against the SDK under test, in real time: the clock is the device's, a scenario
 * that asks to wait waits, and backgrounding the app goes through [host], which has the benchmark do it.
 */
internal class AppScenarioScope(
    override val embrace: SdkApi,
    private val host: ScenarioHost,
) : ScenarioScope {

    override val nowMs: Long
        get() = System.currentTimeMillis()

    override fun advanceTime(ms: Long) {
        Thread.sleep(ms)
    }

    override fun backgroundAndReturn(ms: Long) {
        host.awaitForeground(ms)
    }
}
