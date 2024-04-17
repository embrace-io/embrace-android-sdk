package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.arch.datasource.LogDataSourceImpl
import io.embrace.android.embracesdk.arch.destination.LogEventData
import io.embrace.android.embracesdk.arch.destination.LogWriter
import io.embrace.android.embracesdk.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.EmbType.System.ReactNativeCrash.embAndroidReactNativeCrashJsExceptions
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.opentelemetry.embAndroidThreads
import io.embrace.android.embracesdk.opentelemetry.embCrashNumber
import io.embrace.android.embracesdk.opentelemetry.exceptionMessage
import io.embrace.android.embracesdk.opentelemetry.exceptionStacktrace
import io.embrace.android.embracesdk.opentelemetry.exceptionType
import io.embrace.android.embracesdk.opentelemetry.logRecordUid
import io.embrace.android.embracesdk.payload.JsException
import io.embrace.android.embracesdk.payload.LegacyExceptionInfo
import io.embrace.android.embracesdk.payload.ThreadInfo
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties

/**
 * Intercept and track uncaught Android Runtime exceptions
 */
internal class CrashDataSourceImpl(
    private val logOrchestrator: LogOrchestrator,
    private val sessionOrchestrator: SessionOrchestrator,
    private val sessionProperties: EmbraceSessionProperties,
    private val anrService: AnrService?,
    private val ndkService: NdkService,
    private val preferencesService: PreferencesService,
    private val crashMarker: CrashFileMarker,
    private val logWriter: LogWriter,
    private val configService: ConfigService,
    private val serializer: EmbraceSerializer,
    logger: InternalEmbraceLogger,
) : CrashDataSource,
    LogDataSourceImpl(
        destination = logWriter,
        logger = logger,
        limitStrategy = NoopLimitStrategy,
    ) {

    private var mainCrashHandled = false
    private var jsException: JsException? = null

    /**
     * Handles a crash caught by the [EmbraceUncaughtExceptionHandler] by constructing a
     * JSON message containing a description of the crash, device, and context, and then sending
     * it to the Embrace API.
     *
     * @param exception the exception thrown by the thread
     */
    override fun handleCrash(exception: Throwable) {
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
                configService = configService,
                sessionProperties = sessionProperties
            )

            val crashException = LegacyExceptionInfo.ofThrowable(exception)
            crashAttributes.setAttribute(exceptionType, crashException.name)
            crashAttributes.setAttribute(exceptionMessage, crashException.message ?: "")
            crashAttributes.setAttribute(
                exceptionStacktrace,
                encodeToUTF8String(
                    serializer.toJson(crashException.lines, List::class.java)
                )
            )
            crashAttributes.setAttribute(logRecordUid, crashId)
            crashAttributes.setAttribute(embCrashNumber, crashNumber.toString())
            crashAttributes.setAttribute(
                EmbType.System.Crash.embAndroidCrashExceptionCause,
                encodeToUTF8String(
                    getExceptionCause(exception)
                )
            )
            crashAttributes.setAttribute(
                embAndroidThreads,
                encodeToUTF8String(
                    getThreadsInfo()
                )
            )

            // TODO: add threading through of jsException
            jsException?.let { crashAttributes.setAttribute(embAndroidReactNativeCrashJsExceptions, jsException.toString()) }

            val logEventData = LogEventData(
                schemaType = getSchemaType(crashAttributes),
                message = "",
                severity = Severity.ERROR,
            )

            logWriter.addLog(logEventData)

            // Attempt to send any logs that are still waiting in the sink
            logOrchestrator.flush(true)

            // Attempt to end and send the session
            sessionOrchestrator.endSessionWithCrash(crashId)

            // Indicate that a crash happened so we can know that in the next launch
            crashMarker.mark()
        }
    }

    override fun logUnhandledJsException(exception: JsException) {
        this.jsException = exception
    }

    private fun getSchemaType(attributes: TelemetryAttributes): SchemaType =
        if (attributes.getAttribute(embAndroidReactNativeCrashJsExceptions) != null) {
            SchemaType.ReactNativeCrash(attributes)
        } else {
            SchemaType.Crash(attributes)
        }

    /**
     * @return a String representation of the exception cause.
     */
    private fun getExceptionCause(t: Throwable?): String {
        val result = mutableListOf<LegacyExceptionInfo>()
        var throwable: Throwable? = t
        while (throwable != null && throwable != throwable.cause) {
            val exceptionInfo = LegacyExceptionInfo.ofThrowable(throwable)
            if (result.contains(exceptionInfo)) {
                break
            }
            result.add(0, exceptionInfo)
            throwable = throwable.cause
        }
        val stackString = result.map(LegacyExceptionInfo::toString).toList()
        return serializer.toJson(stackString, List::class.java)
    }

    /**
     * @return a String representation of the current thread list.
     */
    private fun getThreadsInfo(): String {
        val threadsList = Thread.getAllStackTraces().map { ThreadInfo.ofThread(it.key, it.value) }
        val stackString = threadsList.map(ThreadInfo::toString).toList()
        return serializer.toJson(stackString, List::class.java)
    }

    private fun encodeToUTF8String(source: String): String {
        return source.toByteArray().toUTF8String()
    }
}
