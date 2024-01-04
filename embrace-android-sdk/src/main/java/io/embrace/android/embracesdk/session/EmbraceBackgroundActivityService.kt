package io.embrace.android.embracesdk.session

import android.os.Handler
import android.os.Looper
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigListener
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.PayloadMessageCollator.PayloadType
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.worker.BackgroundWorker
import java.util.concurrent.atomic.AtomicInteger

internal class EmbraceBackgroundActivityService(
    private val metadataService: MetadataService,
    processStateService: ProcessStateService,
    private val deliveryService: DeliveryService,
    private val configService: ConfigService,
    private val ndkService: NdkService,
    /**
     * Embrace service dependencies of the background activity session service.
     */
    private val clock: Clock,
    private val payloadMessageCollator: PayloadMessageCollator,
    private val backgroundWorker: BackgroundWorker
) : BackgroundActivityService, ConfigListener {

    private var lastSaved: Long = 0
    private var willBeSaved = false

    /**
     * The active background activity session.
     */
    @Volatile
    var backgroundActivity: Session? = null
    private val manualBkgSessionsSent = AtomicInteger(0)

    var lastSendAttempt: Long
    private var isEnabled = true

    init {
        processStateService.addListener(this)
        lastSendAttempt = clock.now()
        configService.addListener(this)
        if (processStateService.isInBackground) {
            // start background activity capture from a cold start
            startBackgroundActivityCapture(clock.now(), true, Session.LifeEventType.BKGND_STATE)
        }
    }

    override fun sendBackgroundActivity() {
        if (!isEnabled || !verifyManualSendThresholds()) {
            return
        }
        val now = clock.now()
        val backgroundActivityMessage =
            stopBackgroundActivityCapture(now, Session.LifeEventType.BKGND_MANUAL, null)
        // start a new background activity session
        startBackgroundActivityCapture(clock.now(), false, Session.LifeEventType.BKGND_MANUAL)
        if (backgroundActivityMessage != null) {
            deliveryService.sendBackgroundActivity(backgroundActivityMessage)
        }
    }

    override fun handleCrash(crashId: String) {
        if (isEnabled && backgroundActivity != null) {
            val now = clock.now()
            val backgroundActivityMessage =
                stopBackgroundActivityCapture(now, Session.LifeEventType.BKGND_STATE, crashId)
            if (backgroundActivityMessage != null) {
                deliveryService.saveBackgroundActivity(backgroundActivityMessage)
            }
            startBackgroundActivityCapture(clock.now(), false, Session.LifeEventType.BKGND_STATE)
        }
    }

    override fun onForeground(coldStart: Boolean, startupTime: Long, timestamp: Long) {
        if (isEnabled) {
            val backgroundActivityMessage =
                stopBackgroundActivityCapture(timestamp - 1, Session.LifeEventType.BKGND_STATE, null)
            if (backgroundActivityMessage != null) {
                deliveryService.saveBackgroundActivity(backgroundActivityMessage)
            }
            deliveryService.sendBackgroundActivities()
        }
    }

    override fun onBackground(timestamp: Long) {
        if (isEnabled) {
            startBackgroundActivityCapture(timestamp + 1, false, Session.LifeEventType.BKGND_STATE)
        }
    }

    override fun onConfigChange(configService: ConfigService) {
        if (isEnabled && !configService.isBackgroundActivityCaptureEnabled()) {
            isEnabled = false
        } else if (!isEnabled && configService.isBackgroundActivityCaptureEnabled()) {
            isEnabled = true
        }
    }

    /**
     * Save the background activity to disk
     */
    override fun save() {
        if (isEnabled && backgroundActivity != null) {
            if (clock.now() - lastSaved > MIN_INTERVAL_BETWEEN_SAVES) {
                saveNow()
            } else if (!willBeSaved) {
                willBeSaved = true
                saveLater()
            }
        }
    }

    private fun saveNow() {
        backgroundWorker.submit(runnable = ::cacheBackgroundActivity)
        willBeSaved = false
    }

    private fun saveLater() {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(Runnable { saveNow() }, MIN_INTERVAL_BETWEEN_SAVES)
    }

    /**
     * Start the background activity capture by starting the cache service and creating the background
     * session.
     *
     * @param coldStart defines if the action comes from an application cold start or not
     * @param startType defines which is the lifecycle of the session
     */
    private fun startBackgroundActivityCapture(
        startTime: Long,
        coldStart: Boolean,
        startType: Session.LifeEventType
    ) {
        val activity = payloadMessageCollator.buildInitialSession(
            PayloadType.BACKGROUND_ACTIVITY,
            coldStart,
            startType,
            startTime,
            null
        )
        backgroundActivity = activity
        metadataService.setActiveSessionId(activity.sessionId, false)
        if (configService.autoDataCaptureBehavior.isNdkEnabled()) {
            ndkService.updateSessionId(activity.sessionId)
        }
        saveNow()
    }

    /**
     * Stop the background activity capture by stopping the cache service and putting the background
     * session to its final state with all the data collected up to the current point.
     * Build the next background message and attempt to send it.
     *
     * @param endType defines what kind of event ended the background activity capture
     */
    @Synchronized
    private fun stopBackgroundActivityCapture(
        endTime: Long,
        endType: Session.LifeEventType,
        crashId: String?
    ): SessionMessage? {
        val activity = backgroundActivity
        if (activity == null) {
            InternalStaticEmbraceLogger.logError("No background activity to report")
            return null
        }
        val sendBackgroundActivity = payloadMessageCollator.createBackgroundActivityEndMessage(
            activity,
            endTime,
            endType,
            crashId
        )
        backgroundActivity = null
        return payloadMessageCollator.buildBgActivityMessage(sendBackgroundActivity, true)
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
            InternalStaticEmbraceLogger.logWarning(
                "Warning, failed to send background activity. " +
                    "The amount of background activity that can be sent reached the limit.."
            )
            return false
        }
        if (lastSendAttempt < minBackgroundActivityDuration) {
            InternalStaticEmbraceLogger.logWarning(
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
            val activity = backgroundActivity
            if (activity != null) {
                lastSaved = clock.now()
                val endTime = activity.endTime ?: clock.now()
                val cachedActivity = payloadMessageCollator.createBackgroundActivityEndMessage(
                    activity,
                    endTime,
                    null,
                    null
                )
                val message = payloadMessageCollator.buildBgActivityMessage(cachedActivity, false)
                if (message == null) {
                    InternalStaticEmbraceLogger.logDebug("Failed to cache background activity message.")
                    return
                }
                deliveryService.saveBackgroundActivity(message)
            }
        } catch (ex: Exception) {
            InternalStaticEmbraceLogger.logDebug("Error while caching active session", ex)
        }
    }

    companion object {

        /**
         * Minimum time between writes of the background activity to disk
         */
        private const val MIN_INTERVAL_BETWEEN_SAVES: Long = 5000
    }
}
