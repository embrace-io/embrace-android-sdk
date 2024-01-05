package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService

internal class EmbraceSessionService(
    private val processStateService: ProcessStateService,
    ndkService: NdkService,
    private val sessionHandler: SessionHandler,
    deliveryService: DeliveryService,
    isNdkEnabled: Boolean,
    private val configService: ConfigService,
    private val clock: Clock,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : SessionService {

    init {
        if (!this.processStateService.isInBackground) {
            // If the app goes to foreground before the SDK finishes its startup,
            // the session service will not be registered to the activity listener and will not
            // start the cold session.
            // If so, force a cold session start.
            logger.logDebug("Forcing start of new session as app is in foreground.")
            startSession(true, Session.SessionLifeEventType.STATE, clock.now())
        }

        // Send any sessions that were cached and not yet sent.
        deliveryService.sendCachedSessions(isNdkEnabled, ndkService, sessionHandler.getSessionId())
    }

    override fun startSession(
        coldStart: Boolean,
        startType: Session.SessionLifeEventType,
        startTime: Long
    ) {
        val automaticSessionCloserCallback = Runnable {
            try {
                logger.logInfo("Automatic session closing triggered.")
                triggerStatelessSessionEnd(Session.SessionLifeEventType.TIMED)
            } catch (ex: Exception) {
                logger.logError("Error while trying to close the session automatically", ex)
            }
        }

        sessionHandler.onSessionStarted(
            coldStart,
            startType,
            startTime,
            automaticSessionCloserCallback
        )
    }

    override fun handleCrash(crashId: String) {
        sessionHandler.onCrash(crashId)
    }

    override fun onForeground(coldStart: Boolean, startupTime: Long, timestamp: Long) {
        startSession(coldStart, Session.SessionLifeEventType.STATE, timestamp)
    }

    override fun onBackground(timestamp: Long) {
        sessionHandler.onSessionEnded(Session.SessionLifeEventType.STATE, timestamp, false)
    }

    /**
     * It will try to end session. Note that it will either be for MANUAL or TIMED types.
     *
     * @param endType the origin of the event that ends the session.
     */
    override fun triggerStatelessSessionEnd(
        endType: Session.SessionLifeEventType,
        clearUserInfo: Boolean
    ) {
        if (Session.SessionLifeEventType.STATE == endType) {
            logger.logWarning(
                "triggerStatelessSessionEnd is not allowed to be called for SessionLifeEventType=$endType"
            )
            return
        }

        // Ends active session.
        sessionHandler.onSessionEnded(endType, clock.now(), clearUserInfo) ?: return

        // Starts a new session.
        if (!processStateService.isInBackground) {
            logger.logDebug("Forcing start of new session as app is in foreground.")
            startSession(false, endType, clock.now())
        }
    }

    override fun endSessionManually(clearUserInfo: Boolean) {
        val sessionBehavior: SessionBehavior = configService.sessionBehavior
        if (sessionBehavior.getMaxSessionSecondsAllowed() != null) {
            logger.logWarning("Can't close the session, automatic session close enabled.")
            return
        }

        if (sessionBehavior.isAsyncEndEnabled()) {
            logger.logWarning("Can't close the session, session ending in background thread enabled.")
            return
        }
        triggerStatelessSessionEnd(Session.SessionLifeEventType.MANUAL, clearUserInfo)
    }

    override fun close() {
        logger.logInfo("Shutting down EmbraceSessionService")
        sessionHandler.close()
    }
}
