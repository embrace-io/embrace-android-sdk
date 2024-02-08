package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.session.MemoryCleanerService
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties

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
    private val ndkService: NdkService?,
    private val sessionProperties: EmbraceSessionProperties,
    private val internalErrorService: InternalErrorService,
    private val networkConnectivityService: NetworkConnectivityService,
    private val breadcrumbService: BreadcrumbService
) {

    /**
     * Prepares all services/state for a new session. Practically this involves
     * resetting collections in services etc.
     */
    fun prepareForNewSession(startTime: Long, clearUserInfo: Boolean = false) {
        memoryCleanerService.cleanServicesCollections(internalErrorService)
        sessionProperties.clearTemporary()

        if (clearUserInfo) {
            userService.clearAllUserInfo()
            ndkService?.onUserInfoUpdate()
        }

        // Record the connection type at the start of the session.
        networkConnectivityService.networkStatusOnSessionStarted(startTime)
        breadcrumbService.addFirstViewBreadcrumbForSession(startTime)
    }
}
