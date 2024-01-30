package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal class EmbraceSessionService(
    private val deliveryService: DeliveryService,
    private val payloadMessageCollator: PayloadMessageCollator
) : SessionService {

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
