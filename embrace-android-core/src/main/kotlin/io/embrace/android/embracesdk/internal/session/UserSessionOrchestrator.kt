package io.embrace.android.embracesdk.internal.session

/**
 * Orchestrates changes in the user session.
 */
internal interface UserSessionOrchestrator {

    /**
     * Invoked when a new session part is created.
     */
    fun onNewSessionPart()

    /**
     * Invoked when a developer manually requests the end of the current user session.
     */
    fun onManualEnd()

    /**
     * Retrieves metadata on the current user session, if any exists.
     */
    fun currentUserSession(): UserSessionMetadata?
}
