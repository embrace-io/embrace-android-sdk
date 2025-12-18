package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * An object representing a State for a particular session. A valid instance can be used to update or end state tracking for a particular
 * session.
 *
 * Due to the asynchronousness of telemetry instrumentation, session transitions, and state updates (which includes potential delays in
 * detection and instrumentation), these timestamps will not always line up. Therefore, telemetry recorded right when states updates are
 * happening may not precisely represent the true state of system, so while implementations should try to preserve the integrity of the
 * session-state-instrumentation relationships as far as it knows, the telemetry and metadata that is generated may ultimately still be
 * out of sync, which any backend consuming this data should expect and deal with if required.
 */
interface SessionStateToken<T> {
    /**
     * Notify the session that the state was updated to that value at that time. The timestamp must be explicitly passed in by the caller to
     * avoid including into the timestamp the delay between when the transition happened to when instrumentation recorded it.
     */
    fun update(
        updateDetectedTimeMs: Long,
        newValue: T,
        unrecordedTransitions: UnrecordedTransitions = noUnrecordedTransitions
    ): Boolean

    /**
     * End tracking of this state for the current session.
     */
    fun end(unrecordedTransitions: UnrecordedTransitions = noUnrecordedTransitions)
}

/**
 * Defines the count of state transitions that were not recorded since the last time the token received an update.
 */
data class UnrecordedTransitions(
    val notInSession: Int = 0,
    val droppedByInstrumentation: Int = 0,
)

internal val noUnrecordedTransitions = UnrecordedTransitions()
