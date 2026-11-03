package io.embrace.android.embracesdk.internal.logging

/**
 * An immutable copy of what is sent for one [Throwable]: its [className], [message] and stack [frames], each already
 * capped and stringified.
 */
data class ThrowableSnapshot(
    val className: String,
    val message: String?,
    val frames: List<String>,
)
