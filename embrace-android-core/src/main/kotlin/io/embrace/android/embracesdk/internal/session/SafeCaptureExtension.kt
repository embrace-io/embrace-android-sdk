package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Captures a block of data safely, logging any exceptions that occur as an internal error.
 * This is intended for use when building the session/background activity payloads. If an
 * exception is thrown during capture, then we still want to send the request.
 */
public inline fun <R> captureDataSafely(logger: EmbLogger, result: Provider<R>): R? {
    return try {
        result()
    } catch (exc: Throwable) {
        logger.logError(
            "Exception thrown capturing data",
            exc
        )
        logger.trackInternalError(InternalErrorType.SAFE_DATA_CAPTURE_FAIL, exc)
        null
    }
}
