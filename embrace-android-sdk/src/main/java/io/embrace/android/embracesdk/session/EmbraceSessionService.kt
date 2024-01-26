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

    override fun startSessionWithState(timestamp: Long, coldStart: Boolean): Session {
        return startSession(
            InitialEnvelopeParams.SessionParams(
                coldStart,
                LifeEventType.STATE,
                timestamp
            )
        )
    }

    override fun startSessionWithManual(timestamp: Long): Session {
        return startSession(
            InitialEnvelopeParams.SessionParams(
                false,
                LifeEventType.MANUAL,
                timestamp
            )
        )
    }

    override fun endSessionWithState(initial: Session, timestamp: Long) {
        createAndProcessSessionSnapshot(
            FinalEnvelopeParams.SessionParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.STATE,
                endType = SessionSnapshotType.NORMAL_END
            ),
        )
    }

    override fun endSessionWithManual(initial: Session, timestamp: Long) {
        createAndProcessSessionSnapshot(
            FinalEnvelopeParams.SessionParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.MANUAL,
                endType = SessionSnapshotType.NORMAL_END
            ),
        )
    }

    override fun endSessionWithCrash(initial: Session, timestamp: Long, crashId: String) {
        createAndProcessSessionSnapshot(
            FinalEnvelopeParams.SessionParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.STATE,
                crashId = crashId,
                endType = SessionSnapshotType.JVM_CRASH
            )
        )
    }

    /**
     * It performs all corresponding operations in order to start a session.
     */
    private fun startSession(params: InitialEnvelopeParams.SessionParams): Session {
        val session = payloadMessageCollator.buildInitialSession(params)
        periodicSessionCacher.start { onPeriodicCacheActiveSessionImpl(session, clock.now()) }
        return session
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
    private fun onPeriodicCacheActiveSessionImpl(initial: Session, timestamp: Long): SessionMessage {
        synchronized(orchestrationLock) {
            return createAndProcessSessionSnapshot(
                FinalEnvelopeParams.SessionParams(
                    initial = initial,
                    endTime = timestamp,
                    lifeEventType = LifeEventType.STATE,
                    endType = SessionSnapshotType.PERIODIC_CACHE
                ),
            )
        }
    }
}
