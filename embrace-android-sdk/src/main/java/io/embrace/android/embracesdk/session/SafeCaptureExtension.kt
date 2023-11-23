package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

/**
 * Captures a block of data safely, logging any exceptions that occur as an internal error.
 * This is intended for use when building the session/background activity payloads. If an
 * exception is thrown during capture, then we still want to send the request.
 */
internal inline fun <R> captureDataSafely(result: () -> R): R? {
    return try {
        result()
    } catch (exc: Throwable) {
        InternalStaticEmbraceLogger.logger.logError(
            "Exception thrown capturing session data",
            exc
        )
        null
    }
}
