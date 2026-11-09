package io.embrace.android.embracesdk.internal.logging

/**
 * Identifies SDK errors that repeat each other: the same [type], exception class and top stack frame.
 */
internal data class SdkErrorKey(
    val type: SdkErrorType,
    val exceptionClass: String,
    val topFrame: String?,
)

internal fun SdkErrorSnapshot.key() = SdkErrorKey(type, top.className, top.frames.firstOrNull())
