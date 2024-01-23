package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.session.BackgroundActivityService
import io.embrace.android.embracesdk.session.ConfigGate
import io.embrace.android.embracesdk.session.MemoryCleanerService
import io.embrace.android.embracesdk.session.SessionService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties

internal class SessionOrchestratorImpl(
    private val processStateService: ProcessStateService,
    private val sessionService: SessionService,
    backgroundActivityServiceImpl: BackgroundActivityService?,
    clock: Clock,
    private val configService: ConfigService,
    private val memoryCleanerService: MemoryCleanerService,
    private val userService: UserService,
    private val ndkService: NdkService?,
    private val sessionProperties: EmbraceSessionProperties,
    private val internalErrorService: InternalErrorService
) : SessionOrchestrator {

    private val backgroundActivityGate = ConfigGate(backgroundActivityServiceImpl) {
        configService.isBackgroundActivityCaptureEnabled()
    }
    private val backgroundActivityService: BackgroundActivityService?
        get() = backgroundActivityGate.getService()

    init {
        processStateService.addListener(this)
        configService.addListener(backgroundActivityGate)

        if (processStateService.isInBackground) {
            backgroundActivityService?.startBackgroundActivityWithState(true, clock.now())
        } else {
            // If the app goes to foreground before the SDK finishes its startup,
            // the session service will not be registered to the activity listener and will not
            // start the cold session.
            // If so, force a cold session start.
            sessionService.startSessionWithState(true, clock.now())
        }
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        backgroundActivityService?.endBackgroundActivityWithState(timestamp)
        prepareForNewEnvelope()
        sessionService.startSessionWithState(coldStart, timestamp)
    }

    override fun onBackground(timestamp: Long) {
        sessionService.endSessionWithState(timestamp)
        prepareForNewEnvelope()
        backgroundActivityService?.startBackgroundActivityWithState(false, timestamp)
    }

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        if (processStateService.isInBackground) {
            return
        }
        if (configService.sessionBehavior.isSessionControlEnabled()) {
            return
        }
        sessionService.endSessionWithManual()
        prepareForNewEnvelope(clearUserInfo)
        sessionService.startSessionWithManual()
    }

    /**
     * Prepares all services/state for a new envelope. Practically this involves
     * resetting collections in services etc.
     */
    private fun prepareForNewEnvelope(clearUserInfo: Boolean = false) {
        memoryCleanerService.cleanServicesCollections(internalErrorService)
        sessionProperties.clearTemporary()

        if (clearUserInfo) {
            userService.clearAllUserInfo()
            ndkService?.onUserInfoUpdate()
        }
    }
}
