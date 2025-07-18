package io.embrace.android.embracesdk.internal.capture.crash

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSourceImpl
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.otel.attrs.embAndroidThreads
import io.embrace.android.embracesdk.internal.otel.attrs.embCrashNumber
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.EmbType.System.ReactNativeCrash.embAndroidReactNativeCrashJsException
import io.embrace.android.embracesdk.internal.payload.JsException
import io.embrace.android.embracesdk.internal.payload.LegacyExceptionInfo
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.utils.getThreadInfo
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Intercept and track uncaught Android Runtime exceptions
 */
internal class CrashDataSourceImpl(
    private val sessionPropertiesService: SessionPropertiesService,
    private val preferencesService: PreferencesService,
    private val logWriter: LogWriter,
    configService: ConfigService,
    private val serializer: PlatformSerializer,
    private val logger: EmbLogger,
) : CrashDataSource,
    LogDataSourceImpl(
        destination = logWriter,
        logger = logger,
        limitStrategy = NoopLimitStrategy,
    ) {

    private val handlers: CopyOnWriteArrayList<Lazy<CrashTeardownHandler?>> = CopyOnWriteArrayList()
    private var mainCrashHandled = false
    private var jsException: JsException? = null

    init {
        if (configService.autoDataCaptureBehavior.isJvmCrashCaptureEnabled()) {
            registerExceptionHandler()
        }
    }

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

            val crashId = Uuid.getEmbUuid()
            val crashNumber = preferencesService.incrementAndGetCrashNumber()
            val crashAttributes = TelemetryAttributes(
                sessionPropertiesProvider = sessionPropertiesService::getProperties,
            )

            val crashException = LegacyExceptionInfo.ofThrowable(exception)
            crashAttributes.setAttribute(ExceptionAttributes.EXCEPTION_TYPE.key, crashException.name)
            crashAttributes.setAttribute(
                ExceptionAttributes.EXCEPTION_MESSAGE.key,
                crashException.message
                    ?: ""
            )
            crashAttributes.setAttribute(
                ExceptionAttributes.EXCEPTION_STACKTRACE.key,
                encodeToUTF8String(
                    serializer.toJson(crashException.lines, List::class.java),
                ),
            )
            crashAttributes.setAttribute(LogIncubatingAttributes.LOG_RECORD_UID.key, crashId)
            crashAttributes.setAttribute(embCrashNumber, crashNumber.toString())
            crashAttributes.setAttribute(
                EmbType.System.Crash.embAndroidCrashExceptionCause,
                encodeToUTF8String(
                    getExceptionCause(exception),
                )
            )
            crashAttributes.setAttribute(
                embAndroidThreads,
                encodeToUTF8String(
                    getThreadsInfo(),
                ),
            )

            jsException?.let { e ->
                crashAttributes.setAttribute(
                    embAndroidReactNativeCrashJsException,
                    encodeToUTF8String(
                        serializer.toJson(e, JsException::class.java),
                    ),
                )
            }

            logWriter.addLog(getSchemaType(crashAttributes), Severity.ERROR, "")

            // finally, notify other services that need to perform tear down
            handlers.forEach { it.value?.handleCrash(crashId) }
        }
    }

    override fun logUnhandledJsException(exception: JsException) {
        this.jsException = exception
    }

    override fun addCrashTeardownHandler(handler: Lazy<CrashTeardownHandler?>) {
        handler.let { handlers.add(it) }
    }

    private fun getSchemaType(attributes: TelemetryAttributes): SchemaType =
        if (attributes.getAttribute(embAndroidReactNativeCrashJsException) != null) {
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
        return serializer.toJson(result, List::class.java)
    }

    /**
     * @return a String representation of the current thread list.
     */
    private fun getThreadsInfo(): String {
        val threadsList = Thread.getAllStackTraces().map { getThreadInfo(it.key, it.value) }
        return serializer.toJson(threadsList, List::class.java)
    }

    private fun encodeToUTF8String(source: String): String {
        return source.toByteArray().toUTF8String()
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
}
