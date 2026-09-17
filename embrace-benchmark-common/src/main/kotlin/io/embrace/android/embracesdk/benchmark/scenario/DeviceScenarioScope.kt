package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.internal.api.SdkApi

/**
 * Runs a scenario against a real SDK in real time: the clock is the device's, and a scenario that
 * asks to wait waits.
 */
class DeviceScenarioScope(
    override val embrace: SdkApi,
    private val cycler: ForegroundCycler,
) : ScenarioScope {

    override val nowMs: Long
        get() = System.currentTimeMillis()

    override fun advanceTime(ms: Long) {
        Thread.sleep(ms)
    }

    override fun backgroundAndReturn(ms: Long) {
        cycler.cycle(ms)
    }
}
