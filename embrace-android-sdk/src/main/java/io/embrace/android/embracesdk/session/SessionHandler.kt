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
import io.embrace.android.embracesdk.internal.spans.EmbraceAttributes
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.SessionLifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.prefs.PreferencesService
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
    private val sessionProperties: EmbraceSessionProperties,
    private val clock: Clock,
    private val spansService: SpansService,
    private val automaticSessionStopper: ScheduledExecutorService,
    private val sessionPeriodicCacheExecutorService: ScheduledExecutorService
) : Closeable {

    companion object {

        /**
         * The minimum threshold for how long a session must last. Package-private for test accessibility
         */
        private const val minSessionTime = 5000L

        /**
         * Session caching interval in seconds.
         */
        private const val SESSION_CACHING_INTERVAL = 2
    }

    /**
     * The currently active session.
     */
    @Volatile
    private var activeSession: Session? = null

    internal fun getSessionId(): String? = activeSession?.sessionId

    var scheduledFuture: ScheduledFuture<*>? = null
    var closerFuture: ScheduledFuture<*>? = null

    /**
     * Guards session state changes.
     */
    private val lock = Any()

    /**
     * SDK startup time. Only set for cold start sessions.
     */
    var sdkStartupDuration: Long = 0

    /**
     * It performs all corresponding operations in order to start a session.
     */
    fun onSessionStarted(
        coldStart: Boolean,
        startType: SessionLifeEventType,
        startTime: Long,
        automaticSessionCloserCallback: Runnable
    ): SessionMessage? {
        synchronized(lock) {
            logger.logDebug(
                "SessionHandler: running onSessionStarted. coldStart=$coldStart," +
                    " startType=$startType, startTime=$startTime"
            )

            if (!isAllowedToStart()) {
                logger.logDebug("Session not allowed to start.")
                return null
            }

            val session = Session.buildInitialSession(
                Uuid.getEmbUuid(),
                coldStart,
                startType,
                startTime,
                preferencesService.incrementAndGetSessionNumber(),
                userService.loadUserInfoFromDisk(),
                sessionProperties.get()
            )
            activeSession = session
            logDeveloper("SessionHandler", "Started new session. ID=${session.sessionId}")

            // Record the connection type at the start of the session.
            networkConnectivityService.networkStatusOnSessionStarted(session.startTime)

            val sessionMessage = sessionMessageCollator.buildInitialSessionMessage(session)

            metadataService.setActiveSessionId(session.sessionId, true)

            logger.logDebug("Start session sent to delivery service.")

            handleAutomaticSessionStopper(automaticSessionCloserCallback)
            addFirstViewBreadcrumbForSession(startTime)
            startPeriodicCaching { onPeriodicCacheActiveSession() }
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
        endTime: Long,
        clearUserInfo: Boolean
    ): SessionMessage? {
        synchronized(lock) {
            val session = activeSession ?: return null
            logger.logDebug("SessionHandler: running onSessionEnded. endType=$endType, endTime=$endTime")
            val fullEndSessionMessage = createSessionSnapshot(
                SessionSnapshotType.NORMAL_END,
                session,
                sessionProperties,
                spansService.flushSpans(),
                endType,
                endTime
            ) ?: return null

            // Clean every collection of those services which have collections in memory.
            memoryCleanerService.cleanServicesCollections(exceptionService)
            metadataService.removeActiveSessionId(session.sessionId)
            logger.logDebug("Services collections successfully cleaned.")
            sessionProperties.clearTemporary()
            logger.logDebug("Session properties successfully temporary cleared.")
            deliveryService.sendSession(fullEndSessionMessage, SessionMessageState.END)

            if (endType == SessionLifeEventType.MANUAL && clearUserInfo) {
                userService.clearAllUserInfo()
                // Update user info in NDK service
                ndkService.onUserInfoUpdate()
            }

            // clear active session
            activeSession = null
            logger.logDebug("SessionHandler: cleared active session")
            return fullEndSessionMessage
        }
    }

    /**
     * Called when a regular crash happens. It will build a session message with associated crashId,
     * and send it to our servers.
     */
    fun onCrash(crashId: String) {
        synchronized(lock) {
            val session = activeSession ?: return
            logger.logDebug("SessionHandler: running onCrash for $crashId")
            val fullEndSessionMessage = createSessionSnapshot(
                SessionSnapshotType.JVM_CRASH,
                session,
                sessionProperties,
                spansService.flushSpans(EmbraceAttributes.AppTerminationCause.CRASH),
                SessionLifeEventType.STATE,
                clock.now(),
                crashId,
            )
            activeSession = null
            fullEndSessionMessage?.let(deliveryService::saveSessionOnCrash)
        }
    }

    /**
     * Caches the session, with performance information generated up to the current point.
     */
    private fun onPeriodicCacheActiveSession() {
        try {
            onPeriodicCacheActiveSessionImpl(spansService.completedSpans())
        } catch (ex: Exception) {
            logger.logDebug("Error while caching active session", ex)
        }
    }

    /**
     * Called when periodic cache update needs to be performed.
     * It will update current session 's cache state.
     *
     * Note that the session message will not be sent to our servers.
     */
    fun onPeriodicCacheActiveSessionImpl(
        completedSpans: List<EmbraceSpanData>? = null
    ): SessionMessage? {
        synchronized(lock) {
            val session = activeSession ?: return null
            logger.logDeveloper("SessionHandler", "Running periodic cache of active session.")
            val msg = createSessionSnapshot(
                SessionSnapshotType.PERIODIC_CACHE,
                session,
                sessionProperties,
                completedSpans,
                SessionLifeEventType.STATE,
                clock.now()
            )
            msg?.let(deliveryService::saveSessionPeriodicCache)
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
    private fun isAllowedToEnd(endType: SessionLifeEventType, activeSession: Session): Boolean {
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
                    ((clock.now() - activeSession.startTime) < minSessionTime)
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
     * Snapshots the active session. The behavior is controlled by the
     * [SessionSnapshotType] passed to this function.
     */
    private fun createSessionSnapshot(
        endType: SessionSnapshotType,
        activeSession: Session,
        sessionProperties: EmbraceSessionProperties,
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
            closerFuture?.cancel(true)
            closerFuture = this.automaticSessionStopper.schedule(
                automaticSessionStopperCallback,
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
