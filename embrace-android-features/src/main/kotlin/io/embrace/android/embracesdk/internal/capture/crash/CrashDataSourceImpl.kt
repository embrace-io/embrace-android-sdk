package io.embrace.android.embracesdk.internal.capture.crash

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.attrs.embAndroidThreads
import io.embrace.android.embracesdk.internal.arch.attrs.embCrashNumber
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.ReactNativeCrash.embAndroidReactNativeCrashJsException
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.payload.JsException
import io.embrace.android.embracesdk.internal.payload.LegacyExceptionInfo
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.utils.getThreadInfo
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Intercept and track uncaught Android Runtime exceptions
 */
internal class CrashDataSourceImpl(
    private val sessionPropertiesService: SessionPropertiesService,
    private val preferencesService: PreferencesService,
    args: InstrumentationArgs,
    private val serializer: PlatformSerializer,
) : CrashDataSource,
    DataSourceImpl(
        args = args,
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
    @OptIn(IncubatingApi::class)
    override fun handleCrash(exception: Throwable) {
        if (!mainCrashHandled) {
            mainCrashHandled = true

            captureTelemetry {
                val crashId = Uuid.getEmbUuid()
                val crashNumber = preferencesService.incrementAndGetCrashNumber()
                val crashAttributes = TelemetryAttributes(
                    sessionPropertiesProvider = sessionPropertiesService::getProperties,
                )

                val crashException = LegacyExceptionInfo.ofThrowable(exception)
                crashAttributes.setAttribute(ExceptionAttributes.EXCEPTION_TYPE, crashException.name)
                crashAttributes.setAttribute(
                    ExceptionAttributes.EXCEPTION_MESSAGE,
                    crashException.message
                        ?: ""
                )
                crashAttributes.setAttribute(
                    ExceptionAttributes.EXCEPTION_STACKTRACE,
                    encodeToUTF8String(
                        serializer.toJson(crashException.lines, List::class.java),
                    ),
                )
                crashAttributes.setAttribute(LogAttributes.LOG_RECORD_UID, crashId)
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

                addLog(getSchemaType(crashAttributes), LogSeverity.ERROR, "")

                // finally, notify other services that need to perform tear down
                handlers.forEach { it.value?.handleCrash(crashId) }
            }
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
