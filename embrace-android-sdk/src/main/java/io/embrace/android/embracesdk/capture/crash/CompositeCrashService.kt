package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.JsException

internal class CompositeCrashService(
    private val crashServiceProvider: Provider<CrashService>,
    private val crashDataSourceProvider: Provider<CrashDataSource>,
    private val configService: ConfigService,
    private val logger: EmbLogger,
) : CrashService {

    private val useCrashDataSource: Boolean
        get() = configService.oTelBehavior.isBetaEnabled()

    private val baseCrashService: CrashService
        get() = if (useCrashDataSource) crashDataSourceProvider() else crashServiceProvider()

    init {
        if (configService.autoDataCaptureBehavior.isUncaughtExceptionHandlerEnabled() &&
            !ApkToolsConfig.IS_EXCEPTION_CAPTURE_DISABLED
        ) {
            registerExceptionHandler()
        }
    }

    override fun handleCrash(exception: Throwable) {
        baseCrashService.handleCrash(exception)
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
        val embraceHandler = EmbraceUncaughtExceptionHandler(defaultHandler, this, logger)
        Thread.setDefaultUncaughtExceptionHandler(embraceHandler)
    }
}
