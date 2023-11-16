package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.EmbraceAttributes
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService

internal class EmbraceSessionService(
    private val processStateService: ProcessStateService,
    ndkService: NdkService,
    private val sessionHandler: SessionHandler,
    deliveryService: DeliveryService,
    isNdkEnabled: Boolean,
    private val clock: Clock,
    private val spansService: SpansService,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : SessionService, ProcessStateListener {

    companion object {
        private const val TAG = "EmbraceSessionService"

        /**
         * Signals to the API that the application was in the foreground.
         */
        const val APPLICATION_STATE_FOREGROUND = "foreground"

        /**
         * The minimum threshold for how long a session must last. Package-private for test accessibility
         */
        const val minSessionTime = 5000L

        /**
         * Session caching interval in seconds.
         */
        const val SESSION_CACHING_INTERVAL = 2
    }

    /**
     * SDK startup time. Only set for cold start sessions.
     */
    private var sdkStartupDuration: Long = 0

    /**
     * The currently active session.
     */
    @Volatile

    internal var activeSession: Session? = null

    init {
        if (!this.processStateService.isInBackground) {
            // If the app goes to foreground before the SDK finishes its startup,
            // the session service will not be registered to the activity listener and will not
            // start the cold session.
            // If so, force a cold session start.
            logger.logDeveloper(TAG, "Forcing cold start")
            startStateSession(true, clock.now())
        }

        // Send any sessions that were cached and not yet sent.
        deliveryService.sendCachedSessions(isNdkEnabled, ndkService, activeSession?.sessionId)
    }

    /**
     * record the time taken to initialize the SDK
     *
     * @param sdkStartupDuration the time taken to initialize the SDK in milliseconds
     */
    fun setSdkStartupDuration(sdkStartupDuration: Long) {
        logger.logDeveloper(TAG, "Setting startup duration: $sdkStartupDuration")
        this.sdkStartupDuration = sdkStartupDuration
    }

    override fun startSession(coldStart: Boolean, startType: Session.SessionLifeEventType, startTime: Long) {
        val automaticSessionCloserCallback = Runnable {
            try {
                logger.logInfo("Automatic session closing triggered.")
                triggerStatelessSessionEnd(Session.SessionLifeEventType.TIMED)
            } catch (ex: Exception) {
                logger.logError("Error while trying to close the session automatically", ex)
            }
        }

        val sessionMessage = sessionHandler.onSessionStarted(
            coldStart,
            startType,
            startTime,
            automaticSessionCloserCallback,
            this::onPeriodicCacheActiveSession
        )

        if (sessionMessage != null) {
            logger.logDeveloper(TAG, "Session Message is created")
            activeSession = sessionMessage.session
            logger.logDeveloper(TAG, "Active session: " + activeSession?.sessionId)
        } else {
            logger.logDeveloper(TAG, "Session Message is NULL")
        }
    }

    override fun handleCrash(crashId: String) {
        logger.logDeveloper(TAG, "Attempt to handle crash id: $crashId")

        activeSession?.also {
            sessionHandler.onCrash(
                it,
                crashId,
                sdkStartupDuration,
                spansService.flushSpans(EmbraceAttributes.AppTerminationCause.CRASH)
            )
        } ?: logger.logDeveloper(TAG, "Active session is NULL")
    }

    /**
     * Caches the session, with performance information generated up to the current point.
     */

    fun onPeriodicCacheActiveSession() {
        try {
            val session = activeSession ?: return
            sessionHandler.onPeriodicCacheActiveSession(
                session,
                sdkStartupDuration,
                spansService.completedSpans()
            )
        } catch (ex: Exception) {
            logger.logDebug("Error while caching active session", ex)
        }
    }

    override fun onForeground(coldStart: Boolean, startupTime: Long, timestamp: Long) {
        logger.logDeveloper(TAG, "OnForeground. Starting session.")
        startStateSession(coldStart, timestamp)
    }

    private fun startStateSession(coldStart: Boolean, endTime: Long) {
        logger.logDeveloper(TAG, "Start state session. Is cold start: $coldStart")
        startSession(coldStart, Session.SessionLifeEventType.STATE, endTime)
    }

    override fun onBackground(timestamp: Long) {
        logger.logDeveloper(TAG, "OnBackground. Ending session.")
        endSession(Session.SessionLifeEventType.STATE, timestamp)
    }

    /**
     * It will try to end session. Note that it will either be for MANUAL or TIMED types.
     *
     * @param endType the origin of the event that ends the session.
     */
    override fun triggerStatelessSessionEnd(endType: Session.SessionLifeEventType) {
        if (Session.SessionLifeEventType.STATE == endType) {
            logger.logWarning(
                "triggerStatelessSessionEnd is not allowed to be called for SessionLifeEventType=$endType"
            )
            return
        }

        // Ends active session.
        endSession(endType, clock.now())

        // Starts a new session.
        if (!processStateService.isInBackground) {
            logger.logDeveloper(TAG, "Activity is not in background, starting session.")
            startSession(false, endType, clock.now())
        } else {
            logger.logDeveloper(TAG, "Activity in background, not starting session.")
        }
        logger.logInfo("Session successfully closed.")
    }

    /**
     * This will trigger all necessary events to end the current session and send it to the server.
     *
     * @param endType the origin of the event that ends the session.
     */
    private fun endSession(endType: Session.SessionLifeEventType, endTime: Long) {
        logger.logDebug("Will try to end session.")
        val session = activeSession ?: return
        sessionHandler.onSessionEnded(
            endType,
            session,
            sdkStartupDuration,
            endTime,
            spansService.flushSpans()
        )

        // clear active session
        activeSession = null
        logger.logDeveloper(TAG, "Active session cleared")
    }

    override fun close() {
        logger.logInfo("Shutting down EmbraceSessionService")
        sessionHandler.close()
    }
}
