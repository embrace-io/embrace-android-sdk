package io.embrace.android.embracesdk.session

import androidx.annotation.VisibleForTesting
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.EmbraceAttributes
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class EmbraceSessionService(
    private val logger: InternalEmbraceLogger,
    private val configService: ConfigService,
    private val userService: UserService,
    private val networkConnectivityService: NetworkConnectivityService,
    private val metadataService: MetadataService,
    private val breadcrumbService: BreadcrumbService,
    private val ndkService: NdkService?,
    private val internalErrorService: InternalErrorService,
    private val memoryCleanerService: MemoryCleanerService,
    private val deliveryService: DeliveryService,
    private val payloadMessageCollator: PayloadMessageCollator,
    private val sessionProperties: EmbraceSessionProperties,
    private val clock: Clock,
    private val spansService: SpansService,
    private val sessionPeriodicCacheScheduledWorker: ScheduledWorker
) : SessionService {

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
    @VisibleForTesting
    internal var activeSession: Session? = null

    internal fun getSessionId(): String? = activeSession?.sessionId

    var scheduledFuture: ScheduledFuture<*>? = null

    /**
     * Guards session state changes.
     */
    private val lock = Any()

    /**
     * SDK startup time. Only set for cold start sessions.
     */
    @Volatile
    private var sdkStartupDuration: Long? = null

    init {
        // Send any sessions that were cached and not yet sent.
        deliveryService.sendCachedSessions(ndkService, getSessionId())
    }

    override fun endSessionWithCrash(crashId: String) {
        onCrash(crashId)
    }

    override fun startSessionWithState(coldStart: Boolean, timestamp: Long) {
        startSession(coldStart, LifeEventType.STATE, timestamp)
    }

    override fun endSessionWithState(timestamp: Long) {
        endSession(LifeEventType.STATE, timestamp, false)
    }

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        if (configService.sessionBehavior.isSessionControlEnabled()) {
            return
        }

        // Ends active session.
        endSession(LifeEventType.MANUAL, clock.now(), clearUserInfo) ?: return

        // Starts a new session.
        startSession(false, LifeEventType.MANUAL, clock.now())
    }

    override fun setSdkStartupInfo(startTimeMs: Long, endTimeMs: Long) {
        if (sdkStartupDuration == null) {
            spansService.recordCompletedSpan(
                name = "sdk-init",
                startTimeNanos = TimeUnit.MILLISECONDS.toNanos(startTimeMs),
                endTimeNanos = TimeUnit.MILLISECONDS.toNanos(endTimeMs)
            )
        }
        sdkStartupDuration = endTimeMs - startTimeMs
    }

    /**
     * It performs all corresponding operations in order to start a session.
     */
    fun startSession(
        coldStart: Boolean,
        startType: LifeEventType,
        startTime: Long,
    ) {
        synchronized(lock) {
            logger.logDebug(
                "SessionHandler: running onSessionStarted. coldStart=$coldStart," +
                    " startType=$startType, startTime=$startTime"
            )

            val session = payloadMessageCollator.buildInitialSession(
                InitialEnvelopeParams.SessionParams(
                    coldStart,
                    startType,
                    startTime
                )
            )
            activeSession = session
            InternalStaticEmbraceLogger.logDeveloper(
                "SessionHandler",
                "Started new session. ID=${session.sessionId}"
            )

            // Record the connection type at the start of the session.
            networkConnectivityService.networkStatusOnSessionStarted(session.startTime)
            metadataService.setActiveSessionId(session.sessionId, true)

            logger.logDebug("Start session sent to delivery service.")

            breadcrumbService.addFirstViewBreadcrumbForSession(startTime)
            startPeriodicCaching { onPeriodicCacheActiveSession() }
            ndkService?.updateSessionId(session.sessionId)
        }
    }

    /**
     * It performs all corresponding operations in order to end a session.
     */
    fun endSession(
        endType: LifeEventType,
        endTime: Long,
        clearUserInfo: Boolean
    ): SessionMessage? {
        synchronized(lock) {
            val session = activeSession ?: return null
            logger.logDebug("SessionHandler: running onSessionEnded. endType=$endType, endTime=$endTime")
            val fullEndSessionMessage = createSessionSnapshot(
                SessionSnapshotType.NORMAL_END,
                session,
                spansService.flushSpans(),
                endType,
                endTime
            ) ?: return null

            // Clean every collection of those services which have collections in memory.
            memoryCleanerService.cleanServicesCollections(internalErrorService)
            metadataService.removeActiveSessionId(session.sessionId)
            logger.logDebug("Services collections successfully cleaned.")
            sessionProperties.clearTemporary()
            logger.logDebug("Session properties successfully temporary cleared.")
            deliveryService.sendSession(fullEndSessionMessage, SessionSnapshotType.NORMAL_END)

            if (endType == LifeEventType.MANUAL && clearUserInfo) {
                userService.clearAllUserInfo()
                // Update user info in NDK service
                ndkService?.onUserInfoUpdate()
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
                spansService.flushSpans(EmbraceAttributes.AppTerminationCause.CRASH),
                LifeEventType.STATE,
                clock.now(),
                crashId,
            )
            activeSession = null
            fullEndSessionMessage?.let {
                deliveryService.sendSession(
                    it,
                    SessionSnapshotType.JVM_CRASH
                )
            }
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
                completedSpans,
                LifeEventType.STATE,
                clock.now()
            )
            msg?.let { deliveryService.sendSession(it, SessionSnapshotType.PERIODIC_CACHE) }
            return msg
        }
    }

    private fun stopPeriodicSessionCaching() {
        logger.logDebug("Stopping session caching.")
        scheduledFuture?.cancel(false)
    }

    /**
     * It determines if we are allowed to build an end session message.
     */
    private fun isAllowedToEnd(endType: LifeEventType, activeSession: Session): Boolean =
        when (endType) {
            LifeEventType.STATE -> true
            LifeEventType.MANUAL -> (clock.now() - activeSession.startTime) >= minSessionTime
            else -> false // background activity
        }

    /**
     * Snapshots the active session. The behavior is controlled by the
     * [SessionSnapshotType] passed to this function.
     */
    private fun createSessionSnapshot(
        endType: SessionSnapshotType,
        activeSession: Session,
        completedSpans: List<EmbraceSpanData>?,
        lifeEventType: LifeEventType,
        endTime: Long,
        crashId: String? = null,
    ): SessionMessage? {
        if (endType.shouldStopCaching) {
            stopPeriodicSessionCaching()
        }

        if (!isAllowedToEnd(lifeEventType, activeSession)) {
            logger.logDebug("Session not allowed to end.")
            return null
        }

        return payloadMessageCollator.buildFinalSessionMessage(
            initial = activeSession,
            endedCleanly = endType.endedCleanly,
            forceQuit = endType.forceQuit,
            crashId = crashId,
            endType = lifeEventType,
            sdkStartupDuration = sdkStartupDuration ?: 0,
            endTime = endTime,
            spans = completedSpans
        )
    }

    /**
     * It starts a background job that will schedule a callback to do periodic caching.
     */
    private fun startPeriodicCaching(cacheCallback: Runnable) {
        scheduledFuture = this.sessionPeriodicCacheScheduledWorker.scheduleWithFixedDelay(
            cacheCallback,
            0,
            SESSION_CACHING_INTERVAL.toLong(),
            TimeUnit.SECONDS
        )
        logger.logDebug("Periodic session cache successfully scheduled.")
    }
}
