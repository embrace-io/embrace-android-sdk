package io.embrace.android.embracesdk.testframework.actions

/**
 * Timestamps for various events during the simulated execution of an app when we record a session during the integration tests
 */
internal data class SessionTimestamps(
    /**
     * The time when the session begins
     */
    val startTimeMs: Long,

    /**
     * Time when the session foregrounds. This could differ from [startTimeMs] if the session corresponds to the first session
     * of the app instance when background activity is disabled, as the time in the background will be included in the session time.
     */
    val foregroundTimeMs: Long,

    /**
     * The time when the action in the session is invoked
     */
    val actionTimeMs: Long,

    /**
     * The time when the session ended
     */
    val endTimeMs: Long,
)
