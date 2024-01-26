package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.caching.PeriodicSessionCacher

internal class EmbraceSessionService(
    private val deliveryService: DeliveryService,
    private val payloadMessageCollator: PayloadMessageCollator,
    private val clock: Clock,
    private val periodicSessionCacher: PeriodicSessionCacher,
    private val orchestrationLock: Any // synchronises session orchestration. Temporarily passed in.
) : SessionService {

    /**
     * The currently active session.
     */
    @Volatile
    override var activeSession: Session? = null

    override fun startSessionWithState(timestamp: Long, coldStart: Boolean): String {
        return startSession(
            InitialEnvelopeParams.SessionParams(
                coldStart,
                LifeEventType.STATE,
                timestamp
            )
        )
    }

    override fun startSessionWithManual(timestamp: Long): String {
        return startSession(
            InitialEnvelopeParams.SessionParams(
                false,
                LifeEventType.MANUAL,
                timestamp
            )
        )
    }

    override fun endSessionWithState(timestamp: Long) {
        val initial = activeSession ?: return
        createAndProcessSessionSnapshot(
            FinalEnvelopeParams.SessionParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.STATE,
                endType = SessionSnapshotType.NORMAL_END
            ),
        )
        activeSession = null
    }

    override fun endSessionWithManual(timestamp: Long) {
        val initial = activeSession ?: return
        createAndProcessSessionSnapshot(
            FinalEnvelopeParams.SessionParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.MANUAL,
                endType = SessionSnapshotType.NORMAL_END
            ),
        )
        activeSession = null
    }

    override fun endSessionWithCrash(timestamp: Long, crashId: String) {
        val initial = activeSession ?: return
        createAndProcessSessionSnapshot(
            FinalEnvelopeParams.SessionParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.STATE,
                crashId = crashId,
                endType = SessionSnapshotType.JVM_CRASH
            )
        )
        activeSession = null
    }

    /**
     * It performs all corresponding operations in order to start a session.
     */
    private fun startSession(params: InitialEnvelopeParams.SessionParams): String {
        val session = payloadMessageCollator.buildInitialSession(params)
        activeSession = session
        periodicSessionCacher.start(::onPeriodicCacheActiveSessionImpl)
        return session.sessionId
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
        synchronized(orchestrationLock) {
            val initial = activeSession ?: return null
            return createAndProcessSessionSnapshot(
                FinalEnvelopeParams.SessionParams(
                    initial = initial,
                    endTime = clock.now(),
                    lifeEventType = LifeEventType.STATE,
                    endType = SessionSnapshotType.PERIODIC_CACHE
                ),
            )
        }
    }
}
