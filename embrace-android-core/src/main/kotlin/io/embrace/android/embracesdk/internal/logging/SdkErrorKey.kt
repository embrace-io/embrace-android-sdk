package io.embrace.android.embracesdk.internal.logging

/**
 * Identifies SDK errors that repeat each other: the same [type], exception class and top stack frame.
 */
internal data class SdkErrorKey(
    val type: SdkErrorType,
    val exceptionClass: Class<out Throwable>,
    val topFrame: StackTraceElement?,
)
