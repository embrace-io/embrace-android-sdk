package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger

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
    dataSource: JvmCrashDataSource,
    private val logger: InternalLogger,
) : Thread.UncaughtExceptionHandler {

    @Volatile
    private var dataSource: JvmCrashDataSource? = dataSource

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        try {
            dataSource?.logUnhandledJvmThrowable(exception)
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.UncaughtExceptionHandler, ex)
        } finally {
            defaultHandler?.uncaughtException(thread, exception)
        }
    }

    /**
     * Stops forwarding exceptions to the data source and releases it. We continue to delegate to
     * the default handler.
     */
    fun detach() {
        dataSource = null
    }
}
