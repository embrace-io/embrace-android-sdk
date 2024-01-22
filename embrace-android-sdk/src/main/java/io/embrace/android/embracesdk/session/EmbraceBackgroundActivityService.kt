package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.worker.ScheduledWorker
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

internal class EmbraceBackgroundActivityService(
    private val metadataService: MetadataService,
    private val deliveryService: DeliveryService,
    private val configService: ConfigService,
    private val ndkService: NdkService,
    /**
     * Embrace service dependencies of the background activity session service.
     */
    private val clock: Clock,
    private val payloadMessageCollator: PayloadMessageCollator,
    private val scheduledWorker: ScheduledWorker,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : BackgroundActivityService {

    private var lastSaved: Long = 0

    /**
     * The active background activity session.
     */
    @Volatile
    var backgroundActivity: Session? = null
    private val manualBkgSessionsSent = AtomicInteger(0)

    private var lastSendAttempt: Long = clock.now()

    override fun startBackgroundActivityWithState(coldStart: Boolean, timestamp: Long) {
        // kept for backwards compat. the backend expects the start time to be 1 ms greater
        // than the adjacent session, and manually adjusts.
        val time = when {
            coldStart -> timestamp
            else -> timestamp + 1
        }
        startCapture(
            InitialEnvelopeParams.BackgroundActivityParams(
                coldStart = coldStart,
                startType = LifeEventType.BKGND_STATE,
                startTime = time
            )
        )
    }

    override fun endBackgroundActivityWithState(timestamp: Long) {
        // kept for backwards compat. the backend expects the start time to be 1 ms greater
        // than the adjacent session, and manually adjusts.
        val activity = backgroundActivity ?: return
        val message = stopCapture(
            FinalEnvelopeParams.BackgroundActivityParams(
                initial = activity,
                endTime = timestamp - 1,
                lifeEventType = LifeEventType.BKGND_STATE
            )
        )
        deliveryService.saveBackgroundActivity(message)
        deliveryService.sendBackgroundActivities()
    }

    override fun endBackgroundActivityWithCrash(crashId: String) {
        val activity = backgroundActivity ?: return
        val now = clock.now()
        val message = stopCapture(
            FinalEnvelopeParams.BackgroundActivityParams(
                initial = activity,
                endTime = now,
                lifeEventType = LifeEventType.BKGND_STATE,
                crashId = crashId
            )
        )
        deliveryService.saveBackgroundActivity(message)
        startCapture(
            InitialEnvelopeParams.BackgroundActivityParams(
                false,
                LifeEventType.BKGND_STATE,
                clock.now()
            )
        )
    }

    override fun sendBackgroundActivity() {
        if (!verifyManualSendThresholds()) {
            return
        }
        val activity = backgroundActivity ?: return
        val now = clock.now()
        val message = stopCapture(
            FinalEnvelopeParams.BackgroundActivityParams(
                initial = activity,
                endTime = now,
                lifeEventType = LifeEventType.BKGND_MANUAL,
                crashId = null
            )
        )
        // start a new background activity session
        startCapture(
            InitialEnvelopeParams.BackgroundActivityParams(
                coldStart = false,
                startType = LifeEventType.BKGND_MANUAL,
                startTime = clock.now()
            )
        )
        deliveryService.sendBackgroundActivity(message)
    }

    /**
     * Save the background activity to disk
     */
    override fun save() {
        backgroundActivity ?: return
        val delta = clock.now() - lastSaved
        val delay = max(0, MIN_INTERVAL_BETWEEN_SAVES - delta)
        scheduledWorker.schedule<Unit>(::cacheBackgroundActivity, delay, TimeUnit.MILLISECONDS)
    }

    /**
     * Start the background activity capture by starting the cache service and creating the background
     * session.
     */
    private fun startCapture(params: InitialEnvelopeParams.BackgroundActivityParams) {
        val activity = payloadMessageCollator.buildInitialSession(params)
        backgroundActivity = activity
        metadataService.setActiveSessionId(activity.sessionId, false)
        if (configService.autoDataCaptureBehavior.isNdkEnabled()) {
            ndkService.updateSessionId(activity.sessionId)
        }
        save()
    }

    /**
     * Stop the background activity capture by stopping the cache service and putting the background
     * session to its final state with all the data collected up to the current point.
     * Build the next background message and attempt to send it.
     */
    @Synchronized
    private fun stopCapture(params: FinalEnvelopeParams.BackgroundActivityParams): SessionMessage {
        backgroundActivity = null
        return payloadMessageCollator.buildFinalBackgroundActivityMessage(params)
    }

    /**
     * Verify if the amount of background activities captured reach the limit or if the last send
     * attempt was less than 5 sec ago.
     *
     * @return false if the verify failed, true otherwise
     */
    private fun verifyManualSendThresholds(): Boolean {
        val behavior = configService.backgroundActivityBehavior
        val manualBackgroundActivityLimit = behavior.getManualBackgroundActivityLimit()
        val minBackgroundActivityDuration = behavior.getMinBackgroundActivityDuration()
        if (manualBkgSessionsSent.getAndIncrement() >= manualBackgroundActivityLimit) {
            logger.logWarning(
                "Warning, failed to send background activity. " +
                    "The amount of background activity that can be sent reached the limit.."
            )
            return false
        }
        if (lastSendAttempt < minBackgroundActivityDuration) {
            logger.logWarning(
                "Warning, failed to send background activity. The last attempt " +
                    "to send background activity was less than 5 seconds ago."
            )
            return false
        }
        return true
    }

    /**
     * Cache the activity, with performance information generated up to the current point.
     */
    private fun cacheBackgroundActivity() {
        try {
            val activity = backgroundActivity ?: return
            lastSaved = clock.now()
            val message = payloadMessageCollator.buildFinalBackgroundActivityMessage(
                FinalEnvelopeParams.BackgroundActivityParams(
                    initial = activity,
                    endTime = activity.endTime ?: clock.now(),
                    lifeEventType = null
                )
            )
            deliveryService.saveBackgroundActivity(message)
        } catch (ex: Exception) {
            logger.logDebug("Error while caching active session", ex)
        }
    }

    companion object {

        /**
         * Minimum time between writes of the background activity to disk
         */
        private const val MIN_INTERVAL_BETWEEN_SAVES: Long = 5000
    }
}
