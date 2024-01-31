package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal class PayloadFactoryImpl(
    private val deliveryService: DeliveryService,
    private val payloadMessageCollator: PayloadMessageCollator
) : PayloadFactory {

    override fun startBackgroundActivityWithState(timestamp: Long, coldStart: Boolean): Session {
        // kept for backwards compat. the backend expects the start time to be 1 ms greater
        // than the adjacent session, and manually adjusts.
        val time = when {
            coldStart -> timestamp
            else -> timestamp + 1
        }
        return payloadMessageCollator.buildInitialSession(
            InitialEnvelopeParams.BackgroundActivityParams(
                coldStart = coldStart,
                startType = LifeEventType.BKGND_STATE,
                startTime = time
            )
        )
    }

    override fun endBackgroundActivityWithState(initial: Session, timestamp: Long) {
        // kept for backwards compat. the backend expects the start time to be 1 ms greater
        // than the adjacent session, and manually adjusts.
        val message = payloadMessageCollator.buildFinalBackgroundActivityMessage(
            FinalEnvelopeParams.BackgroundActivityParams(
                initial = initial,
                endTime = timestamp - 1,
                lifeEventType = LifeEventType.BKGND_STATE
            )
        )
        deliveryService.saveBackgroundActivity(message)
        deliveryService.sendBackgroundActivities()
    }

    override fun endBackgroundActivityWithCrash(
        initial: Session,
        timestamp: Long,
        crashId: String
    ) {
        val message = payloadMessageCollator.buildFinalBackgroundActivityMessage(
            FinalEnvelopeParams.BackgroundActivityParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.BKGND_STATE,
                crashId = crashId
            )
        )
        deliveryService.saveBackgroundActivity(message)
    }

    override fun snapshotBackgroundActivity(initial: Session, timestamp: Long): SessionMessage {
        val message = payloadMessageCollator.buildFinalBackgroundActivityMessage(
            FinalEnvelopeParams.BackgroundActivityParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = null
            )
        )
        deliveryService.saveBackgroundActivity(message)
        return message
    }

    override fun startSessionWithState(timestamp: Long, coldStart: Boolean): Session {
        return payloadMessageCollator.buildInitialSession(
            InitialEnvelopeParams.SessionParams(
                coldStart,
                LifeEventType.STATE,
                timestamp
            )
        )
    }

    override fun startSessionWithManual(timestamp: Long): Session {
        return payloadMessageCollator.buildInitialSession(
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
     * Called when the session is persisted every 2s to cache its state.
     */
    override fun snapshotSession(initial: Session, timestamp: Long): SessionMessage {
        return createAndProcessSessionSnapshot(
            FinalEnvelopeParams.SessionParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.STATE,
                endType = SessionSnapshotType.PERIODIC_CACHE
            ),
        )
    }

    /**
     * Snapshots the active session. The behavior is controlled by the
     * [SessionSnapshotType] passed to this function.
     */
    private fun createAndProcessSessionSnapshot(params: FinalEnvelopeParams.SessionParams): SessionMessage {
        return payloadMessageCollator.buildFinalSessionMessage(params).also {
            deliveryService.sendSession(it, params.endType)
        }
    }
}
