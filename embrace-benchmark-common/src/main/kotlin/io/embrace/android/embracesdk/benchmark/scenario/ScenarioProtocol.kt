package io.embrace.android.embracesdk.benchmark.scenario

/**
 * The contract between an app that runs a scenario and the benchmark that drives it.
 */
object ScenarioProtocol {

    /**
     * Intent extra naming the [ScenarioSpec] to run, as [PersistenceScenarios.byId] resolves it.
     */
    const val EXTRA_SCENARIO_ID: String = "scenario_id"

    /**
     * Published while the scenario is running and needs nothing from the benchmark.
     */
    const val STATUS_RUNNING: String = "running"

    /**
     * Marks a status the benchmark must act on.
     */
    const val STATUS_PREFIX: String = "bench: "

    /**
     * Followed by the number of milliseconds the app should spend in the background.
     */
    const val STATUS_AWAIT: String = "await "

    /**
     * The scenario ran, the session ended, and its writes have settled.
     */
    const val STATUS_OK: String = "ok"

    /**
     * The SDK never started, so there is nothing to measure.
     */
    const val STATUS_NOT_STARTED: String = "sdk-not-started"

    /**
     * Followed by the reason the scenario could not be completed.
     */
    const val STATUS_ERROR: String = "error "
}
