package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.comms.delivery.SessionMessageState
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.MessageType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.SessionLifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.EmbraceSessionService.Companion.SESSION_CACHING_INTERVAL
import io.embrace.android.embracesdk.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import java.io.Closeable
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class SessionHandler(
    private val logger: InternalEmbraceLogger,
    private val configService: ConfigService,
    private val preferencesService: PreferencesService,
    private val userService: UserService,
    private val networkConnectivityService: NetworkConnectivityService,
    private val metadataService: MetadataService,
    private val breadcrumbService: BreadcrumbService,
    private val activityLifecycleTracker: ActivityTracker,
    private val ndkService: NdkService,
    private val exceptionService: EmbraceInternalErrorService,
    private val memoryCleanerService: MemoryCleanerService,
    private val deliveryService: DeliveryService,
    private val sessionMessageCollator: SessionMessageCollator,
    private val clock: Clock,
    private val automaticSessionStopper: ScheduledExecutorService,
    private val sessionPeriodicCacheExecutorService: ScheduledExecutorService
) : Closeable {

    /**
     * Defines the states in which a session can end.
     */
    private enum class SessionEndType(
        val endedCleanly: Boolean,
        val forceQuit: Boolean,
        val shouldStopCaching: Boolean
    ) {

        /**
         * The end session happened in the normal way (i.e. process state changes or manual/timed end).
         */
        NORMAL_END(true, false, true),

        /**
         * The end session is being constructed so that it can be periodically cached. This avoids
         * the scenario of data loss in the event of NDK crashes.
         */
        CACHED_END(false, true, false),

        /**
         * The end session is being constructed because of a JVM crash.
         */
        JVM_CRASH_END(false, false, true);
    }

    var scheduledFuture: ScheduledFuture<*>? = null

    /**
     * Guards session state changes.
     */
    private val lock = Any()

    /**
     * It performs all corresponding operations in order to start a session.
     */
    fun onSessionStarted(
        coldStart: Boolean,
        startType: SessionLifeEventType,
        startTime: Long,
        sessionProperties: EmbraceSessionProperties,
        automaticSessionCloserCallback: Runnable,
        cacheCallback: Runnable
    ): SessionMessage? {
        synchronized(lock) {
            if (!isAllowedToStart()) {
                logger.logDebug("Session not allowed to start.")
                return null
            }

        logDeveloper("SessionHandler", "Session Started")
        val session = Session.buildStartSession(
            Uuid.getEmbUuid(),
            coldStart,
            startType,
            startTime,
            preferencesService.getIncrementAndGetSessionNumber(),
            userService.loadUserInfoFromDisk(),
            sessionProperties.get()
        )
        logDeveloper("SessionHandler", "SessionId = ${session.sessionId}")

            // Record the connection type at the start of the session.
            networkConnectivityService.networkStatusOnSessionStarted(session.startTime)

            val sessionMessage = sessionMessageCollator.buildStartSessionMessage(session)

            metadataService.setActiveSessionId(session.sessionId)

            deliveryService.sendSession(sessionMessage, SessionMessageState.START)
            logger.logDebug("Start session successfully sent.")

            handleAutomaticSessionStopper(automaticSessionCloserCallback)
            addFirstViewBreadcrumbForSession(startTime)
            startPeriodicCaching(cacheCallback)
            if (configService.autoDataCaptureBehavior.isNdkEnabled()) {
                ndkService.updateSessionId(session.sessionId)
            }

            return sessionMessage
        }
    }

    /**
     * It performs all corresponding operations in order to end a session.
     */
    fun onSessionEnded(
        endType: SessionLifeEventType,
        originSession: Session?,
        sessionProperties: EmbraceSessionProperties,
        sdkStartupDuration: Long,
        endTime: Long,
        completedSpans: List<EmbraceSpanData>? = null
    ) {
        synchronized(lock) {
            logger.logDebug("Will try to run end session full.")
            if (originSession == null) {
                return
            }
            val fullEndSessionMessage = runEndSessionImpl(
                SessionEndType.NORMAL_END,
                originSession,
                sessionProperties,
                sdkStartupDuration,
                completedSpans,
                endType,
                endTime
            ) ?: return

            // Clean every collection of those services which have collections in memory.
            memoryCleanerService.cleanServicesCollections(exceptionService)
            metadataService.removeActiveSessionId(originSession.sessionId)
            logger.logDebug("Services collections successfully cleaned.")
            sessionProperties.clearTemporary()
            logger.logDebug("Session properties successfully temporary cleared.")
            deliveryService.sendSession(fullEndSessionMessage, SessionMessageState.END)
        }
    }

    /**
     * Called when a regular crash happens. It will build a session message with associated crashId,
     * and send it to our servers.
     */
    fun onCrash(
        originSession: Session,
        crashId: String,
        sessionProperties: EmbraceSessionProperties,
        sdkStartupDuration: Long,
        completedSpans: List<EmbraceSpanData>? = null
    ) {
        synchronized(lock) {
            logger.logDebug("Will try to run end session for crash.")
            val fullEndSessionMessage = runEndSessionImpl(
                SessionEndType.JVM_CRASH_END,
                originSession,
                sessionProperties,
                sdkStartupDuration,
                completedSpans,
                SessionLifeEventType.STATE,
                clock.now(),
                crashId,
            )
            fullEndSessionMessage?.let(deliveryService::saveSessionOnCrash)
        }
    }

    /**
     * Called when periodic cache update needs to be performed.
     * It will update current session 's cache state.
     *
     * Note that the session message will not be sent to our servers.
     */
    fun onPeriodicCacheActiveSession(
        activeSession: Session?,
        sessionProperties: EmbraceSessionProperties,
        sdkStartupDuration: Long,
        completedSpans: List<EmbraceSpanData>? = null
    ): SessionMessage? {
        synchronized(lock) {
            val msg = activeSession?.let {
                logger.logDebug("Will try to run end session for caching.")
                runEndSessionImpl(
                    SessionEndType.CACHED_END,
                    activeSession,
                    sessionProperties,
                    sdkStartupDuration,
                    completedSpans,
                    SessionLifeEventType.STATE,
                    clock.now()
                )
            }
            msg?.let(deliveryService::saveSession)
            return msg
        }
    }

    override fun close() {
        stopPeriodicSessionCaching()
    }

    private fun stopPeriodicSessionCaching() {
        logger.logDebug("Stopping session caching.")
        scheduledFuture?.cancel(false)
    }

    /**
     * If maximum timeout session is set through config, then this method starts automatic session
     * stopper job, so session timeouts at given time.
     */
    private fun handleAutomaticSessionStopper(automaticSessionCloserCallback: Runnable) {
        // If getMaxSessionSeconds is not null, schedule the session stopper.
        val maxSessionSecondsAllowed = configService.sessionBehavior.getMaxSessionSecondsAllowed()
        if (maxSessionSecondsAllowed != null) {
            logger.logDebug("Will start automatic session stopper.")
            startAutomaticSessionStopper(
                automaticSessionCloserCallback,
                maxSessionSecondsAllowed
            )
        } else {
            logger.logDebug("Maximum session timeout not set on config. Will not start automatic session stopper.")
        }
    }

    /**
     * It determines if we are allowed to build an end session message.
     */
    private fun isAllowedToEnd(endType: SessionLifeEventType, activeSession: Session?): Boolean {
        if (activeSession == null) {
            logger.logDebug("No active session found. Session is not allowed to end.")
            return false
        }

        return when (endType) {
            SessionLifeEventType.STATE -> {
                // state sessions are always allowed to be ended
                logger.logDebug("Session is STATE, it is always allowed to end.")
                true
            }

            SessionLifeEventType.MANUAL, SessionLifeEventType.TIMED -> {
                logger.logDebug("Session is either MANUAL or TIMED.")
                if (!configService.sessionBehavior.isSessionControlEnabled()) {
                    logger.logWarning(
                        "Session control disabled from remote configuration. " +
                            "Session is not allowed to end."
                    )
                    false
                } else if (endType == SessionLifeEventType.MANUAL &&
                    ((clock.now() - activeSession.startTime) < EmbraceSessionService.minSessionTime)
                ) {
                    // If less than 5 seconds, then the session cannot be finished manually.
                    logger.logError("The session has to be of at least 5 seconds to be ended manually.")
                    false
                } else {
                    logger.logDebug("Session allowed to end.")
                    true
                }
            }
        }
    }

    /**
     * 'Ends' the active session. Note that this logic is also used for caching the session
     * periodically so the session won't always end. The behavior is controlled by the
     * [SessionEndType] passed to this function.
     */
    private fun runEndSessionImpl(
        endType: SessionEndType,
        activeSession: Session,
        sessionProperties: EmbraceSessionProperties,
        sdkStartupDuration: Long,
        completedSpans: List<EmbraceSpanData>?,
        lifeEventType: SessionLifeEventType,
        endTime: Long,
        crashId: String? = null,
    ): SessionMessage? {
        if (endType.shouldStopCaching) {
            stopPeriodicSessionCaching()
        }
        if (!configService.dataCaptureEventBehavior.isMessageTypeEnabled(MessageType.SESSION)) {
            logger.logWarning("Session messages disabled. Ignoring all Sessions.")
            return null
        }
        if (!isAllowedToEnd(lifeEventType, activeSession)) {
            logger.logDebug("Session not allowed to end.")
            return null
        }

        val fullEndSessionMessage = sessionMessageCollator.buildEndSessionMessage(
            activeSession,
            endedCleanly = endType.endedCleanly,
            forceQuit = endType.forceQuit,
            crashId,
            lifeEventType,
            sessionProperties,
            sdkStartupDuration,
            endTime,
            completedSpans
        )
        logger.logDeveloper("SessionHandler", "End session message=$fullEndSessionMessage")
        return fullEndSessionMessage
    }

    /**
     * It starts a background job that will schedule a callback to automatically end the session.
     */
    private fun startAutomaticSessionStopper(
        automaticSessionStopperCallback: Runnable,
        maxSessionSeconds: Int
    ) {
        if (configService.sessionBehavior.isAsyncEndEnabled()) {
            logger.logWarning(
                "Can't close the session. Automatic session closing disabled " +
                    "since async session send is enabled."
            )
            return
        }

        try {
            this.automaticSessionStopper.scheduleWithFixedDelay(
                automaticSessionStopperCallback,
                maxSessionSeconds.toLong(),
                maxSessionSeconds.toLong(),
                TimeUnit.SECONDS
            )
            logger.logDebug("Automatic session stopper successfully scheduled.")
        } catch (e: RejectedExecutionException) {
            // This happens if the executor has shutdown previous to the schedule call
            logger.logError("Cannot schedule Automatic session stopper.", e)
        }
    }

    /**
     * This function add the current view breadcrumb if the app comes from background to foreground
     * or replace the first session view breadcrumb possibly created before the session ir order to
     * have it in the session scope time.
     */
    private fun addFirstViewBreadcrumbForSession(startTime: Long) {
        val screen: String? = breadcrumbService.getLastViewBreadcrumbScreenName()
        if (screen != null) {
            breadcrumbService.replaceFirstSessionView(screen, startTime)
        } else {
            val foregroundActivity = activityLifecycleTracker.foregroundActivity
            if (foregroundActivity != null) {
                breadcrumbService.forceLogView(
                    foregroundActivity.localClassName,
                    startTime
                )
            }
        }
    }

    private fun isAllowedToStart(): Boolean {
        return if (!configService.dataCaptureEventBehavior.isMessageTypeEnabled(MessageType.SESSION)) {
            logger.logWarning("Session messages disabled. Ignoring all sessions.")
            false
        } else {
            logger.logDebug("Session is allowed to start.")
            true
        }
    }

    /**
     * It starts a background job that will schedule a callback to do periodic caching.
     */
    private fun startPeriodicCaching(cacheCallback: Runnable) {
        try {
            scheduledFuture = this.sessionPeriodicCacheExecutorService.scheduleWithFixedDelay(
                cacheCallback,
                0,
                SESSION_CACHING_INTERVAL.toLong(),
                TimeUnit.SECONDS
            )
            logger.logDebug("Periodic session cache successfully scheduled.")
        } catch (e: RejectedExecutionException) {
            // This happens if the executor has shutdown previous to the schedule call
            logger.logError("Cannot schedule Periodic session cache.", e)
        }
    }
}
