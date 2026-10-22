package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode

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
    private val errorHandler: ErrorHandler,
) : Thread.UncaughtExceptionHandler {

    @Volatile
    private var dataSource: JvmCrashDataSource? = dataSource

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        // Run the default crash handler even if the guard method itself throws
        try {
            errorHandler.guardSdkCode(SdkErrorType.UncaughtExceptionHandler) {
                dataSource?.logUnhandledJvmThrowable(exception)
            }
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
