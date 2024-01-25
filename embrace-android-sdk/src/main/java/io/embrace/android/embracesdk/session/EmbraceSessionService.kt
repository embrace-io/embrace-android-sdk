package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.caching.PeriodicSessionCacher

internal class EmbraceSessionService(
    private val logger: InternalEmbraceLogger,
    private val networkConnectivityService: NetworkConnectivityService,
    private val breadcrumbService: BreadcrumbService,
    private val deliveryService: DeliveryService,
    private val payloadMessageCollator: PayloadMessageCollator,
    private val clock: Clock,
    private val periodicSessionCacher: PeriodicSessionCacher
) : SessionService {

    /**
     * The currently active session.
     */
    @Volatile
    override var activeSession: Session? = null

    /**
     * Guards session state changes.
     */
    private val lock = Any()

    override fun startSessionWithState(coldStart: Boolean, timestamp: Long): String {
        return startSession(
            InitialEnvelopeParams.SessionParams(
                coldStart,
                LifeEventType.STATE,
                timestamp
            )
        )
    }

    override fun startSessionWithManual(): String {
        return startSession(
            InitialEnvelopeParams.SessionParams(
                false,
                LifeEventType.MANUAL,
                clock.now()
            )
        )
    }

    override fun endSessionWithState(timestamp: Long) {
        endSessionImpl(LifeEventType.STATE, timestamp)
    }

    override fun endSessionWithManual() {
        endSessionImpl(LifeEventType.MANUAL, clock.now()) ?: return
    }

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

    /**
     * It performs all corresponding operations in order to start a session.
     */
    private fun startSession(params: InitialEnvelopeParams.SessionParams): String {
        synchronized(lock) {
            logger.logDebug(
                "SessionHandler: running onSessionStarted. coldStart=${params.coldStart}," +
                    " startType=${params.startType}, startTime=${params.startTime}"
            )

            val session = payloadMessageCollator.buildInitialSession(
                params
            )
            activeSession = session
            logger.logDeveloper(
                "SessionHandler",
                "Started new session. ID=${session.sessionId}"
            )

            // Record the connection type at the start of the session.
            networkConnectivityService.networkStatusOnSessionStarted(session.startTime)
            breadcrumbService.addFirstViewBreadcrumbForSession(params.startTime)
            periodicSessionCacher.start(::onPeriodicCacheActiveSessionImpl)
            return session.sessionId
        }
    }

    /**
     * It performs all corresponding operations in order to end a session.
     */
    private fun endSessionImpl(
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
            activeSession = null
            logger.logDebug("SessionHandler: cleared active session")
            return fullEndSessionMessage
        }
    }

    /**
     * Snapshots the active session. The behavior is controlled by the
     * [SessionSnapshotType] passed to this function.
     */
    private fun createAndProcessSessionSnapshot(params: FinalEnvelopeParams.SessionParams): SessionMessage {
        if (params.endType.shouldStopCaching) {
            periodicSessionCacher.stop()
        }

        return payloadMessageCollator.buildFinalSessionMessage(params).also {
            deliveryService.sendSession(it, params.endType)
        }
    }

    /**
     * Called when the session is persisted every 2s to cache its state.
     */
    private fun onPeriodicCacheActiveSessionImpl(): SessionMessage? {
        synchronized(lock) {
            val session = activeSession ?: return null
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
}
