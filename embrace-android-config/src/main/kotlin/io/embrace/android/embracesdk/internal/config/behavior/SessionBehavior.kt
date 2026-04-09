package io.embrace.android.embracesdk.internal.config.behavior

interface SessionBehavior {

    /**
     * Whether session control is enabled, meaning that features should be gated.
     */
    fun isSessionControlEnabled(): Boolean

    /**
     * Returns the maximum number of properties that can be attached to a session
     */
    fun getMaxUserSessionProperties(): Int

    /**
     * Returns the maximum allowed duration of a user session in milliseconds.
     * Defaults to 24 hours.
     */
    fun getMaxSessionDurationMs(): Long

    /**
     * Returns the inactivity timeout for a user session in milliseconds.
     * Must be <= [getMaxSessionDurationMs]. Defaults to 30 minutes.
     */
    fun getSessionInactivityTimeoutMs(): Long

    /**
     * Returns the minimum duration a session must last before it can be ended manually,
     * in milliseconds.
     */
    fun getMinSessionDurationMs(): Long
}
