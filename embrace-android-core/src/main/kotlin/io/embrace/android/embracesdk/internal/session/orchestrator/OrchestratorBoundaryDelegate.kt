package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService

/**
 * This class is responsible for orchestrating a clean boundary between sessions.
 * I.e. if a session transitions to a background activity, previous data should be cleared
 * & the relevant services should be updated as necessary.
 *
 * This class acts as a delegate to the SessionOrchestrator & is separated out because it
 * contains references to various services that are otherwise irrelevant to the SessionOrchestrator.
 */
internal class OrchestratorBoundaryDelegate(
    private val userSessionPropertiesService: UserSessionPropertiesService,
) {

    /**
     * Clears state that is scoped to a user session. This will be invoked when the active user session ends,
     * which is AFTER the final session part payload has been created and BEFORE the next session part span is created.
     */
    fun cleanupAfterUserSessionEnd() {
        userSessionPropertiesService.cleanupAfterUserSessionEnd()
    }

    /**
     * Prepare the SDK for a new session part. This will be invoked AFTER the new session part span has been created.
     */
    fun prepareForNewSessionPart() {
        userSessionPropertiesService.prepareForNewSessionPart()
    }
}
