package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

/**
 * Captures a block of data safely, logging any exceptions that occur as an internal error.
 * This is intended for use when building the session/background activity payloads. If an
 * exception is thrown during capture, then we still want to send the request.
 */
internal inline fun <R> captureDataSafely(logger: InternalEmbraceLogger, result: Provider<R>): R? {
    return try {
        result()
    } catch (exc: Throwable) {
        logger.logError(
            "Exception thrown capturing data",
            exc
        )
        null
    }
}
