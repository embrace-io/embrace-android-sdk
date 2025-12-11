package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * An object representing a State for a particular session. A valid instance can be used to update or end state tracking for a particular
 * session
 */
interface SessionStateToken<T> {
    /**
     * Notify the session that the state was updated to that value at that time. The timestamp must be explicitly passed in by the caller to
     * avoid including into the timestamp the delay between when the transition happened to when instrumentation recorded it.
     */
    fun update(updateDetectedTimeMs: Long, newValue: T, droppedTransitions: Int = 0)

    /**
     * End tracking of this state for the current session.
     */
    fun end()
}
