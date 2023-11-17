package io.embrace.android.embracesdk.session

import android.os.Handler
import android.os.Looper
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigListener
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.event.EmbraceRemoteLogger
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.EmbraceAttributes
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.BackgroundActivity
import io.embrace.android.embracesdk.payload.BackgroundActivity.Companion.createStartMessage
import io.embrace.android.embracesdk.payload.BackgroundActivity.Companion.createStopMessage
import io.embrace.android.embracesdk.payload.BackgroundActivity.LifeEventType
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.Breadcrumbs
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.utils.submitSafe
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger

internal class EmbraceBackgroundActivityService(
    private val performanceInfoService: PerformanceInfoService,
    private val metadataService: MetadataService,
    private val breadcrumbService: BreadcrumbService,
    processStateService: ProcessStateService,
    private val eventService: EventService,
    private val remoteLogger: EmbraceRemoteLogger,
    private val userService: UserService,
    private val exceptionService: EmbraceInternalErrorService,
    private val deliveryService: DeliveryService,
    private val configService: ConfigService,
    private val ndkService: NdkService,
    private val preferencesService: PreferencesService,
    /**
     * Embrace service dependencies of the background activity session service.
     */
    private val clock: Clock,
    private val spansService: SpansService,
    private val executorServiceSupplier: Lazy<ExecutorService>
) : BackgroundActivityService, ConfigListener {

    @get:Synchronized
    private val cacheExecutorService: ExecutorService by lazy { executorServiceSupplier.value }
    private var lastSaved: Long = 0
    private var willBeSaved = false

    /**
     * The active background activity session.
     */

    @Volatile
    var backgroundActivity: BackgroundActivity? = null
    private val manualBkgSessionsSent = AtomicInteger(0)

    var lastSendAttempt: Long
    private var isEnabled = true

    init {
        processStateService.addListener(this)
        lastSendAttempt = clock.now()
        configService.addListener(this)
        if (processStateService.isInBackground) {
            // start background activity capture from a cold start
            startBackgroundActivityCapture(clock.now(), true, LifeEventType.BKGND_STATE)
        }
    }

    override fun sendBackgroundActivity() {
        if (!isEnabled || !verifyManualSendThresholds()) {
            return
        }
        val now = clock.now()
        val backgroundActivityMessage =
            stopBackgroundActivityCapture(now, LifeEventType.BKGND_MANUAL, null)
        // start a new background activity session
        startBackgroundActivityCapture(clock.now(), false, LifeEventType.BKGND_MANUAL)
        if (backgroundActivityMessage != null) {
            deliveryService.sendBackgroundActivity(backgroundActivityMessage)
        }
    }

    override fun handleCrash(crashId: String) {
        if (isEnabled && backgroundActivity != null) {
            val now = clock.now()
            val backgroundActivityMessage =
                stopBackgroundActivityCapture(now, LifeEventType.BKGND_STATE, crashId)
            if (backgroundActivityMessage != null) {
                deliveryService.saveBackgroundActivity(backgroundActivityMessage)
            }
            startBackgroundActivityCapture(clock.now(), false, LifeEventType.BKGND_STATE)
        }
    }

    override fun onForeground(coldStart: Boolean, startupTime: Long, timestamp: Long) {
        if (isEnabled) {
            val backgroundActivityMessage =
                stopBackgroundActivityCapture(timestamp - 1, LifeEventType.BKGND_STATE, null)
            if (backgroundActivityMessage != null) {
                deliveryService.saveBackgroundActivity(backgroundActivityMessage)
            }
            deliveryService.sendBackgroundActivities()
        }
    }

    override fun onBackground(timestamp: Long) {
        if (isEnabled) {
            startBackgroundActivityCapture(timestamp + 1, false, LifeEventType.BKGND_STATE)
        }
    }

    override fun onConfigChange(configService: ConfigService) {
        if (isEnabled && !configService.isBackgroundActivityCaptureEnabled()) {
            disableService()
        } else if (!isEnabled && configService.isBackgroundActivityCaptureEnabled()) {
            enableService()
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
        cacheExecutorService.submitSafe(
            Callable<Any?> {
                cacheBackgroundActivity()
                null
            }
        )
        willBeSaved = false
    }

    private fun saveLater() {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(Runnable { saveNow() }, MIN_INTERVAL_BETWEEN_SAVES)
    }

    private fun disableService() {
        isEnabled = false
    }

    private fun enableService() {
        isEnabled = true
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
        startType: LifeEventType
    ) {
        val activity = createStartMessage(
            getEmbUuid(),
            startTime,
            coldStart,
            startType,
            APPLICATION_STATE_BACKGROUND,
            userService.loadUserInfoFromDisk(),
            preferencesService
        )
        backgroundActivity = activity
        metadataService.setActiveSessionId(activity.sessionId)
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
        endType: LifeEventType,
        crashId: String?
    ): BackgroundActivityMessage? {
        val activity = backgroundActivity
        if (activity == null) {
            InternalStaticEmbraceLogger.logError("No background activity to report")
            return null
        }
        val startTime = activity.startTime ?: 0
        val sendBackgroundActivity = createStopMessage(
            activity,
            APPLICATION_STATE_BACKGROUND,
            MESSAGE_TYPE_END,
            endTime,
            eventService.findEventIdsForSession(startTime, endTime),
            remoteLogger.findInfoLogIds(startTime, endTime),
            remoteLogger.findWarningLogIds(startTime, endTime),
            remoteLogger.findErrorLogIds(startTime, endTime),
            remoteLogger.getInfoLogsAttemptedToSend(),
            remoteLogger.getWarnLogsAttemptedToSend(),
            remoteLogger.getErrorLogsAttemptedToSend(),
            exceptionService.currentExceptionError,
            endTime,
            endType,
            remoteLogger.getUnhandledExceptionsSent(),
            crashId
        )
        backgroundActivity = null
        return buildBackgroundActivityMessage(sendBackgroundActivity, true)
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
     * Create the background session message with the current state of the background activity.
     *
     * @param backgroundActivity      the current state of a background activity
     * @param isBackgroundActivityEnd true if the message is being built for the termination of the background activity
     * @return a background activity message for backend
     */
    private fun buildBackgroundActivityMessage(
        backgroundActivity: BackgroundActivity?,
        isBackgroundActivityEnd: Boolean
    ): BackgroundActivityMessage? {
        if (backgroundActivity != null) {
            val startTime = backgroundActivity.startTime ?: 0L
            val endTime = backgroundActivity.endTime ?: clock.now()
            val isCrash = backgroundActivity.crashReportId != null
            val breadcrumbs: Breadcrumbs
            val spans: List<EmbraceSpanData>?
            if (isBackgroundActivityEnd) {
                breadcrumbs = breadcrumbService.flushBreadcrumbs()
                spans = spansService.flushSpans(
                    if (isCrash) {
                        EmbraceAttributes.AppTerminationCause.CRASH
                    } else {
                        null
                    }
                )
            } else {
                breadcrumbs = breadcrumbService.getBreadcrumbs(startTime, endTime)
                spans = spansService.completedSpans()
            }

            return BackgroundActivityMessage(
                backgroundActivity = backgroundActivity,
                userInfo = backgroundActivity.user,
                appInfo = metadataService.getAppInfo(),
                deviceInfo = metadataService.getDeviceInfo(),
                performanceInfo = performanceInfoService.getSessionPerformanceInfo(
                    startTime,
                    endTime,
                    java.lang.Boolean.TRUE == backgroundActivity.isColdStart,
                    null
                ),
                breadcrumbs = breadcrumbs,
                spans = spans
            )
        }
        return null
    }

    /**
     * Cache the activity, with performance information generated up to the current point.
     */
    private fun cacheBackgroundActivity() {
        try {
            val activity = backgroundActivity
            if (activity != null) {
                lastSaved = clock.now()
                val startTime = activity.startTime ?: 0L
                val endTime = activity.endTime ?: clock.now()
                val cachedActivity = createStopMessage(
                    activity,
                    APPLICATION_STATE_BACKGROUND,
                    MESSAGE_TYPE_END,
                    null,
                    eventService.findEventIdsForSession(startTime, endTime),
                    remoteLogger.findInfoLogIds(startTime, endTime),
                    remoteLogger.findWarningLogIds(startTime, endTime),
                    remoteLogger.findErrorLogIds(startTime, endTime),
                    remoteLogger.getInfoLogsAttemptedToSend(),
                    remoteLogger.getWarnLogsAttemptedToSend(),
                    remoteLogger.getErrorLogsAttemptedToSend(),
                    exceptionService.currentExceptionError,
                    clock.now(),
                    null,
                    remoteLogger.getUnhandledExceptionsSent(),
                    null
                )
                val message = buildBackgroundActivityMessage(cachedActivity, false)
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
         * Signals to the API that this is a background session.
         */
        private const val APPLICATION_STATE_BACKGROUND = "background"

        /**
         * Signals to the API the end of a session.
         */
        private const val MESSAGE_TYPE_END = "en"

        /**
         * Minimum time between writes of the background activity to disk
         */
        private const val MIN_INTERVAL_BETWEEN_SAVES: Long = 5000
    }
}
