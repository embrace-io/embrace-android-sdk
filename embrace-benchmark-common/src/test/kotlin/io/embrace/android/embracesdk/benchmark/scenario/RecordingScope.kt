package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.benchmark.fakes.FakeSdkApi

/**
 * Runs a scenario against a fake SDK on a clock of its own, recording what the scenario asked of it.
 */
internal class RecordingScope : ScenarioScope {

    override val embrace: FakeSdkApi = FakeSdkApi { nowMs }

    var elapsedMs: Long = 0
        private set

    var backgroundTransitions: Int = 0
        private set

    val apiCalls: Int
        get() = embrace.telemetryCount

    override val nowMs: Long
        get() = START_TIME_MS + elapsedMs

    override fun advanceTime(ms: Long) {
        elapsedMs += ms
    }

    override fun backgroundAndReturn(ms: Long) {
        backgroundTransitions++
        elapsedMs += ms
    }

    companion object {
        const val START_TIME_MS = 1_692_201_601_000L
    }
}
