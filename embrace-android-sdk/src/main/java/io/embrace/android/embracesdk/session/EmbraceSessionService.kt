package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class EmbraceSessionService(
    private val logger: InternalEmbraceLogger,
    private val networkConnectivityService: NetworkConnectivityService,
    private val sessionIdTracker: SessionIdTracker,
    private val breadcrumbService: BreadcrumbService,
    private val deliveryService: DeliveryService,
    private val payloadMessageCollator: PayloadMessageCollator,
    private val clock: Clock,
    private val sessionPeriodicCacheScheduledWorker: ScheduledWorker
) : SessionService {

    companion object {

        /**
         * Session caching interval in seconds.
         */
        private const val SESSION_CACHING_INTERVAL = 2
    }

    /**
     * The currently active session.
     */
    @Volatile
    override var activeSession: Session? = null

    private var scheduledFuture: ScheduledFuture<*>? = null

    /**
     * Guards session state changes.
     */
    private val lock = Any()

    override fun endSessionWithCrash(crashId: String) {
        synchronized(lock) {
            val session = activeSession ?: return
            logger.logDebug("SessionHandler: running onCrash for $crashId")
            createAndProcessSessionSnapshot(
                FinalEnvelopeParams.SessionParams(
                    initial = session,
                    endTime = clock.now(),
                    lifeEventType = LifeEventType.STATE,
                    crashId = crashId,
                    endType = SessionSnapshotType.JVM_CRASH
                )
            )
            activeSession = null
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
        endSessionImpl(LifeEventType.STATE, timestamp)
    }

    override fun endSessionWithManual() {
        endSessionImpl(LifeEventType.MANUAL, clock.now()) ?: return
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
        endTime: Long
    ): SessionMessage? {
        synchronized(lock) {
            val session = activeSession ?: return null
            logger.logDebug("SessionHandler: running onSessionEnded. endType=$endType, endTime=$endTime")
            val fullEndSessionMessage = createAndProcessSessionSnapshot(
                FinalEnvelopeParams.SessionParams(
                    initial = session,
                    endTime = endTime,
                    lifeEventType = endType,
                    endType = SessionSnapshotType.NORMAL_END
                ),
            )

            // Clean every collection of those services which have collections in memory.
            sessionIdTracker.setActiveSessionId(null, false)

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
            return createAndProcessSessionSnapshot(
                FinalEnvelopeParams.SessionParams(
                    initial = session,
                    endTime = clock.now(),
                    lifeEventType = LifeEventType.STATE,
                    endType = SessionSnapshotType.PERIODIC_CACHE
                ),
            )
        }
    }

    private fun stopPeriodicSessionCaching() {
        logger.logDebug("Stopping session caching.")
        scheduledFuture?.cancel(false)
    }

    /**
     * Snapshots the active session. The behavior is controlled by the
     * [SessionSnapshotType] passed to this function.
     */
    private fun createAndProcessSessionSnapshot(params: FinalEnvelopeParams.SessionParams): SessionMessage {
        if (params.endType.shouldStopCaching) {
            stopPeriodicSessionCaching()
        }

        return payloadMessageCollator.buildFinalSessionMessage(params).also {
            deliveryService.sendSession(it, params.endType)
        }
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
