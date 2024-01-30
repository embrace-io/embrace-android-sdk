package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage

internal class EmbraceBackgroundActivityService(
    private val deliveryService: DeliveryService,
    private val payloadMessageCollator: PayloadMessageCollator
) : BackgroundActivityService {

    override fun startBackgroundActivityWithState(timestamp: Long, coldStart: Boolean): Session {
        // kept for backwards compat. the backend expects the start time to be 1 ms greater
        // than the adjacent session, and manually adjusts.
        val time = when {
            coldStart -> timestamp
            else -> timestamp + 1
        }
        val activity = payloadMessageCollator.buildInitialSession(
            InitialEnvelopeParams.BackgroundActivityParams(
                coldStart = coldStart,
                startType = LifeEventType.BKGND_STATE,
                startTime = time
            )
        )
        return activity
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
}
