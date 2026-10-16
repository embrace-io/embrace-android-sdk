package io.embrace.android.embracesdk.benchmark.scenario

/**
 * The contract between an app that runs a scenario and the benchmark that drives it.
 */
object ScenarioProtocol {

    /**
     * Global setting holding the percentage the app's remote config enables multi-file persistence for.
     */
    const val MULTI_FILE_PERSISTENCE_SETTING: String = "embrace_pct_multi_file_persistence"

    /**
     * Global setting holding the percentage the app's remote config enables opentelemetry-kotlin's regular
     * implementation for.
     */
    const val OTEL_KOTLIN_SDK_SETTING: String = "embrace_pct_otel_kotlin_sdk"

    /**
     * Global setting that, while set, makes a launch restore the recorded fixture instead of starting the
     * SDK, so that nothing of its own lands on disk.
     */
    const val RESTORE_FIXTURE_SETTING: String = "embrace_benchmark_restore_fixture"

    /**
     * Published while the task is running and needs nothing from the benchmark.
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
     * The task succeeded. Every [HostTask] reports it on success, and any other status is a failure.
     */
    const val STATUS_OK: String = "ok"

    /**
     * The SDK never started, so there is nothing to measure.
     */
    const val STATUS_NOT_STARTED: String = "sdk-not-started"

    /**
     * Followed by the reason the task failed.
     */
    const val STATUS_ERROR: String = "error "
}
