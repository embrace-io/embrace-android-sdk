package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

/**
 * Service for handling crashes intercepted by the [EmbraceUncaughtExceptionHandler] and
 * forwarding them on for processing.
 */
interface CrashService {

    /**
     * Logs an unhandled JVM exception
     */
    fun logUnhandledJvmException(exception: Throwable)

    /**
     * Logs an unhandled JS exception
     */
    fun logUnhandledJsException(
        name: String,
        message: String,
        type: String?,
        stacktrace: String?,
    )
}
