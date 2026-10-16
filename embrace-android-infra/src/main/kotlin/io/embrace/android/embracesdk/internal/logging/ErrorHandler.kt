package io.embrace.android.embracesdk.internal.logging

import java.io.Closeable

/**
 * Handles errors for all code in the SDK. It achieves this by wrapping operations in try-catch. If [Throwable] is thrown unexpectedly
 * then for SDK code [guardSdkCode] will report internal diagnostics, and for user-supplied code [guardUserCode] will log a warning.
 *
 * A fixed number of SDK errors are sent per session part. This class buffers errors that happen during SDK init for delivery if the
 * SDK successfully initializes. Once the SDK is disabled, no SDK errors are reported.
 */
interface ErrorHandler : Closeable {

    enum class Severity {
        INFO, WARNING, ERROR
    }

    /**
     * Logs a message to Logcat.
     */
    fun log(msg: String, throwable: Throwable? = null, severity: Severity = Severity.WARNING)

    /**
     * Runs SDK code safely, returning [default] if it throws. The [Throwable] is tracked as an internal error of
     * [type]. [InternalErrorType.Ignored] reports nothing.
     */
    fun <T> guardSdkCode(type: InternalErrorType, default: T, action: () -> T): T

    /**
     * Runs [action] for each of [items], guarding each call separately so one failure doesn't skip the rest.
     * [ConcurrentModificationException] thrown whilst iterating is also guarded.
     */
    fun <T> guardSdkCodeEach(type: InternalErrorType, items: Iterable<T>, action: (T) -> Unit)

    /**
     * Runs user-supplied code. A [Throwable] it throws is printed via logcat.
     */
    fun <T> guardUserCode(action: () -> T): T

    /**
     * Drains buffered errors to [sink] (throwable, timestampMs) on an SDK state transition. A null [sink] closes this object
     * completely.
     */
    fun drain(sink: ((Throwable, Long) -> Unit)?)

    /**
     * Resets the limit on how many errors are sent.
     */
    fun resetLimit()
}

/**
 * Wraps a [Throwable] thrown by user-supplied code.
 */
class UserCodeException(override val cause: Throwable) : RuntimeException(cause)
