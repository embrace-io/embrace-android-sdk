package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class EmbraceBackgroundActivityService(
    private val deliveryService: DeliveryService,
    /**
     * Embrace service dependencies of the background activity session service.
     */
    private val clock: Clock,
    private val payloadMessageCollator: PayloadMessageCollator,
    private val scheduledWorker: ScheduledWorker,
    private val orchestrationLock: Any, // synchronises session orchestration. Temporarily passed in.
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : BackgroundActivityService {

    private var lastSaved: Long = 0

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
        save()
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
    }

    /**
     * Save the background activity to disk
     */
    override fun save() {
        backgroundActivity ?: return
        val delta = clock.now() - lastSaved
        val delay = max(0, MIN_INTERVAL_BETWEEN_SAVES - delta)
        scheduledWorker.schedule<Unit>(
            { cacheBackgroundActivity(clock.now()) },
            delay,
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * Cache the activity, with performance information generated up to the current point.
     */
    private fun cacheBackgroundActivity(timestamp: Long) {
        synchronized(orchestrationLock) {
            try {
                lastSaved = timestamp
                val initial = backgroundActivity ?: return
                val message = payloadMessageCollator.buildFinalBackgroundActivityMessage(
                    FinalEnvelopeParams.BackgroundActivityParams(
                        initial = initial,
                        endTime = timestamp,
                        lifeEventType = null
                    )
                )
                deliveryService.saveBackgroundActivity(message)
            } catch (ex: Exception) {
                logger.logDebug("Error while caching active session", ex)
            }
        }
    }

    companion object {

        /**
         * Minimum time between writes of the background activity to disk
         */
        private const val MIN_INTERVAL_BETWEEN_SAVES: Long = 5000
    }
}
