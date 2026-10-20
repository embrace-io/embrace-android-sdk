package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Captures a block of data safely, logging any exceptions that occur as an SDK error.
 * This is intended for use when building the session/background activity payloads. If an
 * exception is thrown during capture, then we still want to send the request.
 */
internal inline fun <R> captureDataSafely(errorHandler: ErrorHandler, result: Provider<R>): R? {
    return try {
        result()
    } catch (exc: Throwable) {
        errorHandler.trackSdkError(SdkErrorType.SafeDataCaptureFail, exc)
        null
    }
}
