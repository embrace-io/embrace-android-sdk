package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

/**
 * Service for handling crashes intercepted by the [EmbraceUncaughtExceptionHandler] and
 * forwarding them on for processing.
 */
interface JvmCrashService {

    /**
     * Logs an unhandled JVM throwable
     */
    fun logUnhandledJvmThrowable(exception: Throwable)
}
