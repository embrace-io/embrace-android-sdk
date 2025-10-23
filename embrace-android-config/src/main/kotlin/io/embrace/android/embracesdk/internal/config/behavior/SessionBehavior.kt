package io.embrace.android.embracesdk.internal.config.behavior

interface SessionBehavior {

    /**
     * Whether session control is enabled, meaning that features should be gated.
     */
    fun isSessionControlEnabled(): Boolean

    /**
     * Returns the maximum number of properties that can be attached to a session
     */
    fun getMaxSessionProperties(): Int
}
