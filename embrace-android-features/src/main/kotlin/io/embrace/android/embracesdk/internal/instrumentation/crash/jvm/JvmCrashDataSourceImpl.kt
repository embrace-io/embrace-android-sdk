package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.attrs.embAndroidThreads
import io.embrace.android.embracesdk.internal.arch.attrs.embCrashNumber
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.payload.LegacyExceptionInfo
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.utils.getThreadInfo
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Intercept and track uncaught Android Runtime exceptions
 */
internal class JvmCrashDataSourceImpl(
    private val sessionPropertiesService: SessionPropertiesService,
    private val keyValueStore: KeyValueStore,
    args: InstrumentationArgs,
    private val serializer: PlatformSerializer,

    // allows modification of attributes and schema type if necessary.
    private val telemetryModifier: ((TelemetryAttributes) -> SchemaType)? = null,
) : JvmCrashDataSource,
    DataSourceImpl(
        args = args,
        limitStrategy = NoopLimitStrategy,
    ) {

    private val mainCrashHandled = AtomicBoolean(false)
    private val handlers: CopyOnWriteArrayList<CrashTeardownHandler> = CopyOnWriteArrayList()

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
    override fun logUnhandledJvmThrowable(exception: Throwable) {
        if (!mainCrashHandled.getAndSet(true)) {
            captureTelemetry {
                val crashId = Uuid.getEmbUuid()
                val crashNumber = keyValueStore.incrementAndGetCrashNumber()
                val attrs = TelemetryAttributes(
                    sessionPropertiesProvider = sessionPropertiesService::getProperties,
                )

                val crashException = LegacyExceptionInfo.ofThrowable(exception)
                attrs.setAttribute(
                    ExceptionAttributes.EXCEPTION_TYPE,
                    crashException.name
                )
                attrs.setAttribute(
                    ExceptionAttributes.EXCEPTION_MESSAGE,
                    crashException.message
                        ?: ""
                )
                attrs.setAttribute(
                    ExceptionAttributes.EXCEPTION_STACKTRACE,
                    encodeToUTF8String(
                        serializer.toJson(crashException.lines, List::class.java),
                    ),
                )
                attrs.setAttribute(LogAttributes.LOG_RECORD_UID, crashId)
                attrs.setAttribute(embCrashNumber, crashNumber.toString())
                attrs.setAttribute(
                    EmbType.System.Crash.embAndroidCrashExceptionCause,
                    encodeToUTF8String(
                        getExceptionCause(exception),
                    )
                )
                attrs.setAttribute(
                    embAndroidThreads,
                    encodeToUTF8String(
                        getThreadsInfo(),
                    ),
                )
                val schemaType = telemetryModifier?.invoke(attrs) ?: SchemaType.JvmCrash(attrs)
                addLog(schemaType, LogSeverity.ERROR, "")

                // finally, notify other services that need to perform tear down
                handlers.forEach { it.handleCrash(crashId) }
            }
        }
    }

    override fun addCrashTeardownHandler(handler: CrashTeardownHandler) {
        handlers.add(handler)
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
     * Registers the Embrace [Thread.UncaughtExceptionHandler] to intercept uncaught
     * exceptions and forward them to the Embrace API as crashes.
     */
    private fun registerExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        val embraceHandler = EmbraceUncaughtExceptionHandler(defaultHandler, this, logger)
        Thread.setDefaultUncaughtExceptionHandler(embraceHandler)
    }
}
