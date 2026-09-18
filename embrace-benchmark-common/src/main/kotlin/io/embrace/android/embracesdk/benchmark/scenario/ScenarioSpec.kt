package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.internal.api.SdkApi

/**
 * A named block of telemetry-producing work, written against the public Embrace API.
 */
class ScenarioSpec(
    val id: String,
    val description: String,
    val action: ScenarioScope.() -> Unit,
)

/**
 * What a scenario is given to work with: the SDK under test, and control over the passage of time
 * and the app's foreground state.
 */
interface ScenarioScope {

    /**
     * The SDK under test.
     */
    val embrace: SdkApi

    /**
     * The current scenario time.
     */
    val nowMs: Long

    /**
     * Moves the scenario clock forward..
     */
    fun advanceTime(ms: Long)

    /**
     * Backgrounds the app for [ms], which ends the current session part, then returns to the
     * foreground and starts a new one.
     */
    fun backgroundAndReturn(ms: Long)
}
