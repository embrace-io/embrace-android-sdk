package io.embrace.android.embracesdk.internal.capture.crash

import io.embrace.android.embracesdk.internal.payload.JsException

/**
 * Service for handling crashes intercepted by the [EmbraceUncaughtExceptionHandler] and
 * forwarding them on for processing.
 */
interface CrashService {

    /**
     * Handles crashes from the [EmbraceUncaughtExceptionHandler].
     *
     * @param exception the exception thrown by the thread
     */
    fun handleCrash(exception: Throwable)

    /**
     * Associates an unhandled JS exception with a crash
     *
     * @param exception the [JsException] to associate with the crash
     */
    fun logUnhandledJsException(exception: JsException)
}
