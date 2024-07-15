package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.ndk.NdkService

internal interface SessionIdTracker {

    /**
     * Gets the currently active session ID, if present.
     *
     * @return an optional containing the currently active session ID
     */
    fun getActiveSessionId(): String?

    /**
     * Sets the currently active session ID.
     *
     * @param sessionId the session ID that is currently active
     * @param isSession true if it's a session, false if it's a background activity
     */
    fun setActiveSessionId(sessionId: String?, isSession: Boolean)

    /**
     * Reference to the NDK service. This is set later on as bootstrapping is currently required
     * in the dependency graph.
     */
    var ndkService: NdkService?
}
