package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService

internal class EmbraceSessionService(
    private val processStateService: ProcessStateService,
    ndkService: NdkService,
    private val sessionHandler: SessionHandler,
    deliveryService: DeliveryService,
    isNdkEnabled: Boolean,
    private val clock: Clock
) : SessionService {

    init {
        if (!this.processStateService.isInBackground) {
            // If the app goes to foreground before the SDK finishes its startup,
            // the session service will not be registered to the activity listener and will not
            // start the cold session.
            // If so, force a cold session start.
            sessionHandler.onSessionStarted(true, LifeEventType.STATE, clock.now())
        }

        // Send any sessions that were cached and not yet sent.
        deliveryService.sendCachedSessions(isNdkEnabled, ndkService, sessionHandler.getSessionId())
    }

    override fun handleCrash(crashId: String) {
        sessionHandler.onCrash(crashId)
    }

    override fun onForeground(coldStart: Boolean, startupTime: Long, timestamp: Long) {
        sessionHandler.onSessionStarted(coldStart, LifeEventType.STATE, timestamp)
    }

    override fun onBackground(timestamp: Long) {
        sessionHandler.onSessionEnded(LifeEventType.STATE, timestamp, false)
    }

    override fun endSessionManually(clearUserInfo: Boolean) {
        // Ends active session.
        sessionHandler.onSessionEnded(LifeEventType.MANUAL, clock.now(), clearUserInfo) ?: return

        // Starts a new session.
        if (!processStateService.isInBackground) {
            sessionHandler.onSessionStarted(false, LifeEventType.MANUAL, clock.now())
        }
    }
}
