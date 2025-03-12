package io.embrace.android.embracesdk.testframework.actions

/**
 * Timestamps for various events during the simulated execution of an app when we record a session during the integration tests
 */
internal data class AppExecutionTimestamps(
    /**
     * The time the simulated app execution started.
     */
    var executionStartTimeMs: Long = 0L,

    /**
     * The first time the app was foregrounded during the simulated execution.
     *
     * It should correspond to the start of the first session.
     */
    var firstForegroundTimeMs: Long = 0L,

    /**
     * The time just before the action within the session is executed for the first session in the execution
     *
     * This differs from the session creation time (aka foreground time) because some amount of time is ticked off as the app
     * goes through the activity lifecycle stages.
     */
    var firstActionTimeMs: Long = 0L,

    /**
     * The last time the app was backgrounded during the execution simulation.
     *
     * It should correspond to the end of the last session.
     */
    var lastBackgroundTimeMs: Long = 0L,
)
