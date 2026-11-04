package io.embrace.android.embracesdk.internal.logging

/**
 * An immutable copy of an SDK error of [type] captured at [timestampMs].
 *
 * [chain] starts with the thrown [Throwable] and is followed by its causes. [suppressed] holds the exceptions suppressed by
 * the thrown [Throwable].
 */
data class SdkErrorSnapshot(
    val type: SdkErrorType,
    val timestampMs: Long,
    val chain: List<ThrowableSnapshot>,
    val suppressed: List<ThrowableSnapshot> = emptyList(),
) {

    /**
     * The thrown [Throwable].
     */
    val top: ThrowableSnapshot
        get() = chain.first()
}
