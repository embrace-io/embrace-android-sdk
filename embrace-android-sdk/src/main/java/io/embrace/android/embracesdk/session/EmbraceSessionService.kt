package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session.LifeEventType

internal class EmbraceSessionService(
    ndkService: NdkService,
    private val sessionHandler: SessionHandler,
    deliveryService: DeliveryService,
    isNdkEnabled: Boolean,
    private val clock: Clock,
    private val configService: ConfigService
) : SessionService {

    init {
        // Send any sessions that were cached and not yet sent.
        deliveryService.sendCachedSessions(isNdkEnabled, ndkService, sessionHandler.getSessionId())
    }

    override fun endSessionWithCrash(crashId: String) {
        sessionHandler.onCrash(crashId)
    }

    override fun startSessionWithState(coldStart: Boolean, timestamp: Long) {
        sessionHandler.onSessionStarted(coldStart, LifeEventType.STATE, timestamp)
    }

    override fun endSessionWithState(timestamp: Long) {
        sessionHandler.onSessionEnded(LifeEventType.STATE, timestamp, false)
    }

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        if (configService.sessionBehavior.isSessionControlEnabled()) {
            return
        }

        // Ends active session.
        sessionHandler.onSessionEnded(LifeEventType.MANUAL, clock.now(), clearUserInfo) ?: return

        // Starts a new session.
        sessionHandler.onSessionStarted(false, LifeEventType.MANUAL, clock.now())
    }
}
