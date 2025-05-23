package io.embrace.android.embracesdk.internal.capture.crash

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

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
    private val crashService: CrashService,
    private val logger: EmbLogger,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        try {
            crashService.handleCrash(exception)
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.UNCAUGHT_EXC_HANDLER, ex)
        } finally {
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
}
