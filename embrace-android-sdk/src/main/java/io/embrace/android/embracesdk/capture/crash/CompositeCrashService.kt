package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.JsException

internal class CompositeCrashService(
    private val crashService: CrashService,
    private val crashDataSource: CrashDataSource,
    private val configService: ConfigService,
    private val logger: InternalEmbraceLogger,
) : CrashService {

    private val useV2CrashService: Boolean
        get() = configService.oTelBehavior.isBetaEnabled()

    private val baseCrashService: CrashService
        get() = if (useV2CrashService) crashDataSource else crashService

    init {
        if (configService.autoDataCaptureBehavior.isUncaughtExceptionHandlerEnabled() &&
            !ApkToolsConfig.IS_EXCEPTION_CAPTURE_DISABLED
        ) {
            registerExceptionHandler()
        }
    }

    override fun handleCrash(thread: Thread, exception: Throwable) {
        baseCrashService.handleCrash(thread, exception)
    }

    override fun logUnhandledJsException(exception: JsException) {
        baseCrashService.logUnhandledJsException(exception)
    }

    /**
     * Registers the Embrace [java.lang.Thread.UncaughtExceptionHandler] to intercept uncaught
     * exceptions and forward them to the Embrace API as crashes.
     */
    private fun registerExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        val embraceHandler = EmbraceUncaughtExceptionHandler(defaultHandler, baseCrashService, logger)
        Thread.setDefaultUncaughtExceptionHandler(embraceHandler)
    }
}
