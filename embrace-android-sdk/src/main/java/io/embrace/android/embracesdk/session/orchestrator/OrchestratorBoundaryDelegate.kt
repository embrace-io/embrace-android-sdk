package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.session.MemoryCleanerService
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties

/**
 * This class is responsible for orchestrating a clean boundary between session envelopes.
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
    private val internalErrorService: InternalErrorService
) {

    /**
     * Prepares all services/state for a new envelope. Practically this involves
     * resetting collections in services etc.
     */
    fun prepareForNewEnvelope(clearUserInfo: Boolean = false) {
        memoryCleanerService.cleanServicesCollections(internalErrorService)
        sessionProperties.clearTemporary()

        if (clearUserInfo) {
            userService.clearAllUserInfo()
            ndkService?.onUserInfoUpdate()
        }
    }
}
