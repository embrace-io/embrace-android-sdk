package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug

/**
 * Intercepts uncaught exceptions from the JVM and forwards them to the Embrace API. Once handled,
 * the exception is then delegated to the default [Thread.UncaughtExceptionHandler].
 */
internal class EmbraceUncaughtExceptionHandler(

    /**
     * The default uncaught exception handler; is null if not set.
     */
    private val defaultHandler: Thread.UncaughtExceptionHandler?,

    /**
     * The crash service which will submit the exception to the API as a crash
     */
    private val crashService: CrashService
) : Thread.UncaughtExceptionHandler {

    init {
        logDebug("Registered EmbraceUncaughtExceptionHandler")
    }

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        try {
            crashService.handleCrash(thread, exception)
        } catch (ex: Exception) {
            logDebug("Error occurred in the uncaught exception handler", ex)
        } finally {
            logDebug("Finished handling exception. Delegating to default handler.", exception)
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
}
