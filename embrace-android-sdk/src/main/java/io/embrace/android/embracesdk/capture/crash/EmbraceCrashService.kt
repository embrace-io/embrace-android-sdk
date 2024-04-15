package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.JsException
import io.embrace.android.embracesdk.payload.extensions.CrashFactory
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService

/**
 * Intercepts uncaught Java exceptions and forwards them to the Embrace API.
 */
internal class EmbraceCrashService(
    configService: ConfigService,
    private val logOrchestrator: LogOrchestrator,
    private val sessionOrchestrator: SessionOrchestrator,
    private val sessionPropertiesService: SessionPropertiesService,
    private val metadataService: MetadataService,
    private val sessionIdTracker: SessionIdTracker,
    private val deliveryService: DeliveryService,
    private val userService: UserService,
    private val eventService: EventService,
    private val anrService: AnrService?,
    private val ndkService: NdkService,
    private val gatingService: GatingService,
    private val preferencesService: PreferencesService,
    private val crashMarker: CrashFileMarker,
    private val clock: Clock,
    private val logger: InternalEmbraceLogger
) : CrashService {

    private var mainCrashHandled = false
    private var jsException: JsException? = null

    init {
        if (configService.autoDataCaptureBehavior.isUncaughtExceptionHandlerEnabled() && !ApkToolsConfig.IS_EXCEPTION_CAPTURE_DISABLED) {
            registerExceptionHandler()
        }
    }

    /**
     * Handles a crash caught by the [EmbraceUncaughtExceptionHandler] by constructing a
     * JSON message containing a description of the crash, device, and context, and then sending
     * it to the Embrace API.
     *
     * @param thread    the crashing thread
     * @param exception the exception thrown by the thread
     */
    override fun handleCrash(thread: Thread, exception: Throwable) {
        if (!mainCrashHandled) {
            mainCrashHandled = true

            // Stop ANR tracking first to avoid capture ANR when crash message is being sent
            anrService?.forceAnrTrackingStopOnCrash()

            // Check if the unity crash id exists. If so, means that the native crash capture
            // is enabled for an Unity build. When a native crash occurs and the NDK sends an
            // uncaught exception the SDK assign the unity crash id as the java crash id.
            val unityCrashId = ndkService.getUnityCrashId()
            val crashNumber = preferencesService.incrementAndGetCrashNumber()
            val crash = if (unityCrashId != null) {
                CrashFactory.ofThrowable(logger, exception, jsException, crashNumber, unityCrashId)
            } else {
                CrashFactory.ofThrowable(logger, exception, jsException, crashNumber)
            }

            val sessionId = sessionIdTracker.getActiveSessionId()

            val event = Event(
                CRASH_REPORT_EVENT_NAME,
                null,
                getEmbUuid(),
                sessionId,
                EventType.CRASH,
                clock.now(),
                null,
                false,
                null,
                metadataService.getAppState(),
                null,
                sessionPropertiesService.getProperties(),
                eventService.getActiveEventIds(),
                null,
                null,
                null,
                null
            )
            val versionedEvent = EventMessage(
                event,
                crash,
                metadataService.getDeviceInfo(),
                metadataService.getAppInfo(),
                userService.getUserInfo(),
                null,
                null,
                ApiClient.MESSAGE_VERSION,
                null
            )

            // Sanitize crash event
            val crashEvent = gatingService.gateEventMessage(versionedEvent)

            // Send the crash. This is not guaranteed to succeed since the process is terminating
            // and the request is made on a background executor, but data analysis shows that
            // a surprising % of crashes make it through based on the receive time. Therefore we
            // attempt to send the crash and if it fails, we will send it again on the next launch.
            deliveryService.sendCrash(crashEvent, true)

            // Attempt to send any logs that are still waiting in the sink
            logOrchestrator.flush()

            // End, cache and send the session
            sessionOrchestrator.endSessionWithCrash(crash.crashId)

            // Indicate that a crash happened so we can know that in the next launch
            crashMarker.mark()
        }
    }

    /**
     * Registers the Embrace [java.lang.Thread.UncaughtExceptionHandler] to intercept uncaught
     * exceptions and forward them to the Embrace API as crashes.
     */
    private fun registerExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        val embraceHandler = EmbraceUncaughtExceptionHandler(defaultHandler, this, logger)
        Thread.setDefaultUncaughtExceptionHandler(embraceHandler)
    }

    /**
     * Associates an unhandled JS exception with a crash
     *
     * @param exception the unhandled JS exception
     */
    override fun logUnhandledJsException(exception: JsException) {
        this.jsException = exception
    }

    companion object {
        private const val CRASH_REPORT_EVENT_NAME = "_crash_report"
    }
}
