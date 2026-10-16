package io.embrace.android.embracesdk.internal.logging

/**
 * Runs SDK code safely. If a [Throwable] is thrown it will be tracked as an internal error of [type].
 * [InternalErrorType.Ignored] reports nothing.
 */
inline fun InternalErrorHandler.guardSdkCode(type: InternalErrorType, action: () -> Unit): Unit =
    guardSdkCode(type, Unit, action)

/**
 * As [guardSdkCode], but returns a default value after throws.
 */
inline fun <T> InternalErrorHandler.guardSdkCode(
    type: InternalErrorType,
    default: T,
    action: () -> T,
): T = try {
    action()
} catch (exc: Throwable) {
    reportSdkCodeError(type, exc)
    default
}

/**
 * Runs user-supplied code safely. If a [Throwable] is thrown it will be logged to Logcat.
 */
inline fun <T> guardUserCode(action: () -> T): T = try {
    action()
} catch (exc: UserCodeException) {
    throw exc
} catch (exc: Throwable) {
    throw UserCodeException(exc)
}

@PublishedApi
internal class UserCodeException(override val cause: Throwable) : RuntimeException(cause)

@PublishedApi
internal fun InternalErrorHandler.reportSdkCodeError(type: InternalErrorType, exc: Throwable) {
    if (type == InternalErrorType.Ignored) {
        return
    }
    if (exc is UserCodeException) {
        trackUserCodeError(type, exc.cause)
    } else {
        trackInternalError(type, exc)
    }
}
