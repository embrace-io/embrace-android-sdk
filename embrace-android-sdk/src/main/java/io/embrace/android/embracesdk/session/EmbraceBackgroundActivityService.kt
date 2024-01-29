package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.session.caching.PeriodicBackgroundActivityCacher

internal class EmbraceBackgroundActivityService(
    private val deliveryService: DeliveryService,
    private val payloadMessageCollator: PayloadMessageCollator,
    private val clock: Clock,
    private val periodicCacher: PeriodicBackgroundActivityCacher, // synchronises session orchestration. Temporarily passed in.
    private val orchestrationLock: Any
) : BackgroundActivityService {

    /**
     * The active background activity session.
     */
    @Volatile
    private var backgroundActivity: Session? = null

    override fun startBackgroundActivityWithState(timestamp: Long, coldStart: Boolean): String {
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
        backgroundActivity = activity
        saveBackgroundActivitySnapshot()
        return activity.sessionId
    }

    override fun endBackgroundActivityWithState(timestamp: Long) {
        // kept for backwards compat. the backend expects the start time to be 1 ms greater
        // than the adjacent session, and manually adjusts.
        val initial = backgroundActivity ?: return
        val message = payloadMessageCollator.buildFinalBackgroundActivityMessage(
            FinalEnvelopeParams.BackgroundActivityParams(
                initial = initial,
                endTime = timestamp - 1,
                lifeEventType = LifeEventType.BKGND_STATE
            )
        )
        backgroundActivity = null
        deliveryService.saveBackgroundActivity(message)
        deliveryService.sendBackgroundActivities()
        periodicCacher.stop()
    }

    override fun endBackgroundActivityWithCrash(timestamp: Long, crashId: String) {
        val initial = backgroundActivity ?: return
        val message = payloadMessageCollator.buildFinalBackgroundActivityMessage(
            FinalEnvelopeParams.BackgroundActivityParams(
                initial = initial,
                endTime = timestamp,
                lifeEventType = LifeEventType.BKGND_STATE,
                crashId = crashId
            )
        )
        backgroundActivity = null
        deliveryService.saveBackgroundActivity(message)
        periodicCacher.stop()
    }

    /**
     * Saves a snapshot of the current background activity message to disk
     */
    override fun saveBackgroundActivitySnapshot() {
        periodicCacher.scheduleSave {
            synchronized(orchestrationLock) {
                val initial = backgroundActivity ?: return@synchronized
                val timestamp = clock.now()
                val message = payloadMessageCollator.buildFinalBackgroundActivityMessage(
                    FinalEnvelopeParams.BackgroundActivityParams(
                        initial = initial,
                        endTime = timestamp,
                        lifeEventType = null
                    )
                )
                deliveryService.saveBackgroundActivity(message)
            }
            null
        }
    }
}
