package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
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
    private val sessionPropertiesService: SessionPropertiesService,
    private val networkConnectivityService: NetworkConnectivityService
) {

    /**
     * Prepares all services/state for a new session. Practically this involves
     * resetting collections in services etc.
     */
    fun prepareForNewSession(clearUserInfo: Boolean = false) {
        memoryCleanerService.cleanServicesCollections()
        sessionPropertiesService.clearTemporary()

        if (clearUserInfo) {
            userService.clearAllUserInfo()
        }
    }

    fun onSessionStarted(startTime: Long) {
        // Record the connection type at the start of the session.
        networkConnectivityService.networkStatusOnSessionStarted(startTime)
    }
}
