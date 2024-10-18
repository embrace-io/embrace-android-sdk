package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.session.MemoryCleanerService

/**
 * This class is responsible for orchestrating a clean boundary between sessions.
 * I.e. if a session transitions to a background activity, previous data should be cleared
 * & the relevant services should be updated as necessary.
 *
 * This class acts as a delegate to the SessionOrchestrator & is separated out because it
 * contains references to various services that are otherwise irrelevant to the SessionOrchestrator.
 */
internal class OrchestratorBoundaryDelegate(
    private val memoryCleanerService: MemoryCleanerService,
    private val userService: UserService,
    private val sessionPropertiesService: SessionPropertiesService
) {

    /**
     * Prepares all services/state for a new session. Practically this involves
     * resetting collections in services etc.. This will be invoked AFTER the final session payload has been created.
     */
    fun cleanupAfterSessionEnd(clearUserInfo: Boolean = false) {
        memoryCleanerService.cleanServicesCollections()
        sessionPropertiesService.cleanupAfterSessionEnd()

        if (clearUserInfo) {
            userService.clearAllUserInfo()
        }
    }

    /**
     * Prepare the SDK to create another session. This will be invoked AFTER a new session span has been created.
     */
    fun prepareForNewSession() {
        sessionPropertiesService.prepareForNewSession()
    }
}
