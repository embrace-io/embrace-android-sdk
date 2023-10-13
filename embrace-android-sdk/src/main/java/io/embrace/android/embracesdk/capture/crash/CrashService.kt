package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.payload.JsException

/**
 * Service for handling crashes intercepted by the [EmbraceUncaughtExceptionHandler] and
 * forwarding them on for processing.
 */
internal interface CrashService {

    /**
     * Handles crashes from the [EmbraceUncaughtExceptionHandler].
     *
     * @param thread    the crashing thread
     * @param exception the exception thrown by the thread
     */
    fun handleCrash(thread: Thread, exception: Throwable)

    /**
     * Associates an unhandled JS exception with a crash
     *
     * @param exception the [JsException] to associate with the crash
     */
    fun logUnhandledJsException(exception: JsException)
}
