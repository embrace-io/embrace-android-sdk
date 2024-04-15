package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.arch.datasource.LogDataSourceImpl
import io.embrace.android.embracesdk.arch.destination.LogEventData
import io.embrace.android.embracesdk.arch.destination.LogWriter
import io.embrace.android.embracesdk.arch.limits.OnOffLimitStrategy
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.EmbType.System.ReactNativeCrash.embAndroidReactNativeCrashJsExceptions
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.opentelemetry.embAndroidThreads
import io.embrace.android.embracesdk.opentelemetry.embCrashNumber
import io.embrace.android.embracesdk.opentelemetry.exceptionMessage
import io.embrace.android.embracesdk.opentelemetry.exceptionStacktrace
import io.embrace.android.embracesdk.opentelemetry.exceptionType
import io.embrace.android.embracesdk.payload.JsException
import io.embrace.android.embracesdk.payload.LegacyExceptionInfo
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import java.lang.Thread.UncaughtExceptionHandler

/**
 * Intercept and track uncaught Android Runtime exceptions
 */
internal class CrashDataSource(
    configService: ConfigService,
    private val sessionOrchestrator: SessionOrchestrator,
    private val sessionProperties: EmbraceSessionProperties,
    private val anrService: AnrService?,
    private val ndkService: NdkService,
    @Suppress("UnusedPrivateMember")
    private val gatingService: GatingService,
    private val preferencesService: PreferencesService,
    private val crashMarker: CrashFileMarker,
    private val logger: InternalEmbraceLogger,
    private val logWriter: LogWriter
) : UncaughtExceptionHandler,
    LogDataSourceImpl(
        destination = logWriter,
        logger = logger,
        limitStrategy = OnOffLimitStrategy { configService.autoDataCaptureBehavior.isUncaughtExceptionHandlerEnabled() }
    ) {

    private var mainCrashHandled = false
    private var jsException: JsException? = null
    private val defaultHandler: UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    init {
        if (configService.autoDataCaptureBehavior.isUncaughtExceptionHandlerEnabled()) {
            Thread.setDefaultUncaughtExceptionHandler(this)
        }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            handleCrash(e)
        } catch (ex: Exception) {
            logger.logDebug("Error occurred in the uncaught exception handler", ex)
        } finally {
            logger.logDebug("Finished handling exception. Delegating to default handler.", e)
            defaultHandler?.uncaughtException(t, e)
        }
    }

    /**
     * Handles a crash caught by the [EmbraceUncaughtExceptionHandler] by constructing a
     * JSON message containing a description of the crash, device, and context, and then sending
     * it to the Embrace API.
     *
     * @param exception the exception thrown by the thread
     */
    private fun handleCrash(exception: Throwable) {
        if (!mainCrashHandled) {
            mainCrashHandled = true

            // Stop ANR tracking first to avoid capture ANR when crash message is being sent
            anrService?.forceAnrTrackingStopOnCrash()

            // Check if the unity crash id exists. If so, means that the native crash capture
            // is enabled for an Unity build. When a native crash occurs and the NDK sends an
            // uncaught exception the SDK assign the unity crash id as the java crash id.
            val crashId = ndkService.getUnityCrashId() ?: getEmbUuid()
            val crashNumber = preferencesService.incrementAndGetCrashNumber()
            val crashAttributes = TelemetryAttributes(
                sessionProperties = sessionProperties
            )

            // TODO: make sure serialized JSON values in strings is corrected and as expected
            val crashException = LegacyExceptionInfo.ofThrowable(exception)
            crashAttributes.setAttribute(exceptionType, crashException.name)
            crashAttributes.setAttribute(exceptionMessage, crashException.message ?: "")
            crashAttributes.setAttribute(exceptionStacktrace, crashException.lines.toString())
            crashAttributes.setAttribute(embCrashNumber, crashNumber.toString())
            crashAttributes.setAttribute(EmbType.System.Crash.embAndroidCrashExceptionCause, getCause(exception.cause))
            crashAttributes.setAttribute(embAndroidThreads, crashNumber.toString())

            // TODO: add threading through of jsException
            jsException?.let { crashAttributes.setAttribute(embAndroidReactNativeCrashJsExceptions, jsException.toString()) }

            val logEventData = LogEventData(
                schemaType = getSchemaType(crashAttributes),
                message = "",
                severity = Severity.ERROR,
            )

            logWriter.addLog(logEventData)

            // TODO: apply gating service logic

            // Attempt to end and send the session
            sessionOrchestrator.endSessionWithCrash(crashId)

            // Indicate that a crash happened so we can know that in the next launch
            crashMarker.mark()
        }
    }

    private fun getSchemaType(attributes: TelemetryAttributes): SchemaType =
        if (attributes.getAttribute(embAndroidReactNativeCrashJsExceptions) != null) {
            SchemaType.ReactNativeCrash(attributes)
        } else {
            SchemaType.Crash(attributes)
        }

    private fun getCause(throwable: Throwable?): String {
        val result = mutableListOf<LegacyExceptionInfo>()
        var cause = throwable
        while (cause != null) {
            val exceptionInfo = LegacyExceptionInfo.ofThrowable(cause)
            result.add(0, exceptionInfo)
            cause = cause.cause
        }
        return result.toString()
    }
}
