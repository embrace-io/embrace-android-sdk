package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.EmbraceEvent
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
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Crash
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.JsException
import io.embrace.android.embracesdk.session.BackgroundActivityService
import io.embrace.android.embracesdk.session.SessionService

/**
 * Intercepts uncaught Java exceptions and forwards them to the Embrace API.
 */
internal class EmbraceCrashService(
    configService: ConfigService,
    private val sessionService: SessionService,
    private val metadataService: MetadataService,
    private val deliveryService: DeliveryService,
    private val userService: UserService,
    private val eventService: EventService,
    private val anrService: AnrService?,
    private val ndkService: NdkService,
    private val gatingService: GatingService,
    private val backgroundActivityService: BackgroundActivityService?,
    private val crashMarker: CrashFileMarker,
    private val clock: Clock
) : CrashService {

    private var mainCrashHandled = false
    private var jsException: JsException? = null

    init {
        if (configService.autoDataCaptureBehavior.isUncaughtExceptionHandlerEnabled() && !ApkToolsConfig.IS_EXCEPTION_CAPTURE_DISABLED) {
            logDeveloper("EmbraceCrashService", "crash handler enabled")
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
        logDeveloper("EmbraceCrashService", "Attempting to handle crash")
        if (!mainCrashHandled) {
            mainCrashHandled = true

            // Stop ANR tracking first to avoid capture ANR when crash message is being sent
            anrService?.forceAnrTrackingStopOnCrash()
            logDeveloper(
                "EmbraceCrashService",
                "JsException is present: ${if (jsException != null) "true" else "false"}"
            )

            // Check if the unity crash id exists. If so, means that the native crash capture
            // is enabled for an Unity build. When a native crash occurs and the NDK sends an
            // uncaught exception the SDK assign the unity crash id as the java crash id.
            val unityCrashId = ndkService.getUnityCrashId()
            val crash = if (unityCrashId != null) {
                logDeveloper(
                    "EmbraceCrashService",
                    "unityCrashId is $unityCrashId"
                )
                Crash.ofThrowable(exception, jsException, unityCrashId)
            } else {
                Crash.ofThrowable(exception, jsException)
            }
            logDeveloper("EmbraceCrashService", "crashId = " + crash.crashId)

            val optionalSessionId = metadataService.activeSessionId
            val sessionId = if (optionalSessionId != null) {
                logDeveloper("EmbraceCrashService", "Session id is present:$optionalSessionId")
                optionalSessionId
            } else {
                logDeveloper("EmbraceCrashService", "Session id is not present:")
                null
            }

            val event = Event(
                CRASH_REPORT_EVENT_NAME,
                null,
                getEmbUuid(),
                sessionId,
                EmbraceEvent.Type.CRASH,
                clock.now(),
                null,
                false,
                null,
                metadataService.getAppState(),
                null,
                sessionService.getProperties(),
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
            logDeveloper("EmbraceCrashService", "Attempting to send event...")

            // Save the crash to file
            deliveryService.saveCrash(crashEvent)
            // End, cache and send the session
            sessionService.handleCrash(crash.crashId)
            backgroundActivityService?.handleCrash(crash.crashId)

            // Send the crash. This is not guaranteed to succeed since the process is terminating
            // and the request is made on a background executor, but data analysis shows that
            // a surprising % of crashes make it through based on the receive time. Therefore we
            // attempt to send the crash and if it fails, we will send it again on the next launch.
            deliveryService.sendCrash(crashEvent)

            // Indicate that a crash happened so we can know that in the next launch
            crashMarker.mark()
        }
    }

    /**
     * Registers the Embrace [java.lang.Thread.UncaughtExceptionHandler] to intercept uncaught
     * exceptions and forward them to the Embrace API as crashes.
     */
    private fun registerExceptionHandler() {
        logDeveloper("EmbraceCrashService", "registerExceptionHandler")
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        val embraceHandler = EmbraceUncaughtExceptionHandler(defaultHandler, this)
        Thread.setDefaultUncaughtExceptionHandler(embraceHandler)
    }

    /**
     * Associates an unhandled JS exception with a crash
     *
     * @param exception the unhandled JS exception
     */
    override fun logUnhandledJsException(exception: JsException) {
        logDeveloper("EmbraceCrashService", "logUnhandledJsException")
        this.jsException = exception
    }

    companion object {
        private const val CRASH_REPORT_EVENT_NAME = "_crash_report"
    }
}
