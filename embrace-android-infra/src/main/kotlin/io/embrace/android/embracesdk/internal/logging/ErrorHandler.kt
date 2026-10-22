package io.embrace.android.embracesdk.internal.logging

/**
 * Handles errors for all code in the SDK. It achieves this by wrapping operations in try-catch. If [Throwable] is thrown unexpectedly
 * then for SDK code [guardSdkCode] will report internal diagnostics, and for user-supplied code [guardUserCode] will log a warning.
 *
 * A fixed number of SDK errors are sent per session part. This class buffers errors that happen during SDK init for delivery if the
 * SDK successfully initializes. Once the SDK is disabled, no SDK errors are reported.
 */
interface ErrorHandler : AutoCloseable {

    enum class Severity {
        INFO, WARNING, ERROR
    }

    /**
     * Logs a message to Logcat.
     */
    fun log(msg: String, throwable: Throwable? = null, severity: Severity = Severity.WARNING)

    /**
     * Tracks an SDK error. This is sent to our own telemetry so should be used sparingly
     * & only for states that we can take actions to improve.
     */
    fun trackSdkError(type: SdkErrorType, throwable: Throwable)

    /**
     * Drains buffered errors to [sink] (throwable, timestampMs) on an SDK state transition. Errors captured after that go to
     * [sink] directly. A null [sink] closes this object completely.
     */
    fun drain(sink: ErrorRecorder?)

    /**
     * Resets the limit on how many errors are sent.
     */
    fun resetLimit()
}

/**
 * Runs SDK code safely, returning [default] if it throws. The [Throwable] is tracked as an SDK error of
 * [type]. [SdkErrorType.Ignored] reports nothing.
 */
inline fun <T> ErrorHandler.guardSdkCode(type: SdkErrorType, default: T, action: () -> T): T = try {
    action()
} catch (exc: Throwable) {
    trackSdkError(type, exc)
    default
}

/**
 * Runs SDK code safely, ignoring any result. The [Throwable] is tracked as an SDK error of [type].
 * [SdkErrorType.Ignored] reports nothing.
 */
inline fun ErrorHandler.guardSdkCode(type: SdkErrorType, action: () -> Unit): Unit = guardSdkCode(type, Unit, action)

/**
 * Runs [action] for each of [items], guarding each call separately so one failure doesn't skip the rest.
 * [ConcurrentModificationException] thrown whilst iterating is also guarded.
 */
inline fun <T> ErrorHandler.guardSdkCodeEach(type: SdkErrorType, items: Iterable<T>, action: (T) -> Unit) {
    try {
        items.forEach { item -> guardSdkCode(type) { action(item) } }
    } catch (exc: ConcurrentModificationException) {
        trackSdkError(type, exc)
    }
}

/**
 * Runs user-supplied code. A [Throwable] it throws is printed via logcat.
 */
inline fun <T> ErrorHandler.guardUserCode(action: () -> T): T = try {
    action()
} catch (exc: UserCodeException) {
    throw exc
} catch (exc: Throwable) {
    throw UserCodeException(exc)
}

/**
 * Wraps a [Throwable] thrown by user-supplied code.
 */
class UserCodeException(override val cause: Throwable) : RuntimeException(cause)

/**
 * Signature for a function that records a [Throwable] as an SDK error at the given timestamp [Long]
 */
typealias ErrorRecorder = (Throwable, Long) -> Unit
