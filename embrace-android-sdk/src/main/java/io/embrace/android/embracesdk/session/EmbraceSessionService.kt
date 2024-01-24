package io.embrace.android.embracesdk.session

import androidx.annotation.VisibleForTesting
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class EmbraceSessionService(
    private val logger: InternalEmbraceLogger,
    private val userService: UserService,
    private val networkConnectivityService: NetworkConnectivityService,
    private val sessionIdTracker: SessionIdTracker,
    private val breadcrumbService: BreadcrumbService,
    private val ndkService: NdkService?,
    private val deliveryService: DeliveryService,
    private val payloadMessageCollator: PayloadMessageCollator,
    private val clock: Clock,
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

    private var scheduledFuture: ScheduledFuture<*>? = null

    /**
     * Guards session state changes.
     */
    private val lock = Any()

    init {
        // Send any sessions that were cached and not yet sent.
        deliveryService.sendCachedSessions(ndkService, getSessionId())
    }

    override fun endSessionWithCrash(crashId: String) {
        synchronized(lock) {
            val session = activeSession ?: return
            logger.logDebug("SessionHandler: running onCrash for $crashId")
            val fullEndSessionMessage = createSessionSnapshot(
                FinalEnvelopeParams.SessionParams(
                    initial = session,
                    endTime = clock.now(),
                    lifeEventType = LifeEventType.STATE,
                    crashId = crashId,
                    endType = SessionSnapshotType.JVM_CRASH
                )
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

    override fun startSessionWithState(coldStart: Boolean, timestamp: Long) {
        startSession(
            InitialEnvelopeParams.SessionParams(
                coldStart,
                LifeEventType.STATE,
                timestamp
            )
        )
    }

    override fun endSessionWithState(timestamp: Long) {
        endSessionImpl(LifeEventType.STATE, timestamp, false)
    }

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        endSessionImpl(LifeEventType.MANUAL, clock.now(), clearUserInfo) ?: return
    }

    override fun startSessionWithManual() {
        startSession(
            InitialEnvelopeParams.SessionParams(
                false,
                LifeEventType.MANUAL,
                clock.now()
            )
        )
    }

    /**
     * It performs all corresponding operations in order to start a session.
     */
    internal fun startSession(params: InitialEnvelopeParams.SessionParams) {
        synchronized(lock) {
            logger.logDebug(
                "SessionHandler: running onSessionStarted. coldStart=${params.coldStart}," +
                    " startType=${params.startType}, startTime=${params.startTime}"
            )

            val session = payloadMessageCollator.buildInitialSession(
                params
            )
            activeSession = session
            InternalStaticEmbraceLogger.logDeveloper(
                "SessionHandler",
                "Started new session. ID=${session.sessionId}"
            )

            // Record the connection type at the start of the session.
            sessionIdTracker.setActiveSessionId(session.sessionId, true)
            ndkService?.updateSessionId(session.sessionId)
            networkConnectivityService.networkStatusOnSessionStarted(session.startTime)
            breadcrumbService.addFirstViewBreadcrumbForSession(params.startTime)
            startPeriodicCaching { Systrace.trace("snapshot-session") { onPeriodicCacheActiveSession() } }
        }
    }

    /**
     * It performs all corresponding operations in order to end a session.
     */
    internal fun endSessionImpl(
        endType: LifeEventType,
        endTime: Long,
        clearUserInfo: Boolean
    ): SessionMessage? {
        synchronized(lock) {
            val session = activeSession ?: return null
            logger.logDebug("SessionHandler: running onSessionEnded. endType=$endType, endTime=$endTime")
            val fullEndSessionMessage = createSessionSnapshot(
                FinalEnvelopeParams.SessionParams(
                    initial = session,
                    endTime = endTime,
                    lifeEventType = endType,
                    endType = SessionSnapshotType.NORMAL_END
                ),
            ) ?: return null

            // Clean every collection of those services which have collections in memory.
            sessionIdTracker.setActiveSessionId(null, false)
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
     * Caches the session, with performance information generated up to the current point.
     */
    private fun onPeriodicCacheActiveSession() {
        try {
            onPeriodicCacheActiveSessionImpl()
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
    internal fun onPeriodicCacheActiveSessionImpl(): SessionMessage? {
        synchronized(lock) {
            val session = activeSession ?: return null
            logger.logDeveloper("SessionHandler", "Running periodic cache of active session.")
            val msg = createSessionSnapshot(
                FinalEnvelopeParams.SessionParams(
                    initial = session,
                    endTime = clock.now(),
                    lifeEventType = LifeEventType.STATE,
                    endType = SessionSnapshotType.PERIODIC_CACHE
                ),
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
    private fun isAllowedToEnd(endType: LifeEventType?, activeSession: Session): Boolean =
        when (endType) {
            LifeEventType.STATE -> true
            LifeEventType.MANUAL -> (clock.now() - activeSession.startTime) >= minSessionTime
            else -> false // background activity
        }

    /**
     * Snapshots the active session. The behavior is controlled by the
     * [SessionSnapshotType] passed to this function.
     */
    private fun createSessionSnapshot(params: FinalEnvelopeParams.SessionParams): SessionMessage? {
        if (params.endType.shouldStopCaching) {
            stopPeriodicSessionCaching()
        }

        if (!isAllowedToEnd(params.lifeEventType, params.initial)) {
            logger.logDebug("Session not allowed to end.")
            return null
        }

        return payloadMessageCollator.buildFinalSessionMessage(params)
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
