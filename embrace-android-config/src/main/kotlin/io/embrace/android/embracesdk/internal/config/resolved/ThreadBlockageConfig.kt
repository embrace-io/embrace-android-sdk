package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_STACKTRACE_SIZE_LIMIT

/**
 * Resolved thread blockage config.
 */
interface ThreadBlockageConfig {
    val captureEnabled: Boolean
    val sampleIntervalMs: Long
    val maxStacktracesPerInterval: Int
    val stacktraceFrameLimit: Int
    val maxIntervalsPerSession: Int
    val minDurationMs: Int

    companion object {
        const val DEFAULT_SAMPLE_INTERVAL_MS: Long = 100
        const val DEFAULT_MAX_STACKTRACES_PER_INTERVAL: Int = 80
        const val DEFAULT_MAX_INTERVALS_PER_SESSION: Int = 5
        const val DEFAULT_MIN_DURATION_MS: Int = 1000
    }
}

/**
 * Creates a [ThreadBlockageConfig]. A provider returning null means the default is used.
 */
inline fun ThreadBlockageConfig(
    crossinline captureEnabled: () -> Boolean? = { null },
    crossinline sampleIntervalMs: () -> Long? = { null },
    crossinline maxStacktracesPerInterval: () -> Int? = { null },
    crossinline stacktraceFrameLimit: () -> Int? = { null },
    crossinline maxIntervalsPerSession: () -> Int? = { null },
    crossinline minDurationMs: () -> Int? = { null },
): ThreadBlockageConfig = object : ThreadBlockageConfig {
    override val captureEnabled: Boolean = captureEnabled() ?: true
    override val sampleIntervalMs: Long = sampleIntervalMs() ?: ThreadBlockageConfig.DEFAULT_SAMPLE_INTERVAL_MS
    override val maxStacktracesPerInterval: Int =
        maxStacktracesPerInterval() ?: ThreadBlockageConfig.DEFAULT_MAX_STACKTRACES_PER_INTERVAL
    override val stacktraceFrameLimit: Int = stacktraceFrameLimit() ?: DEFAULT_STACKTRACE_SIZE_LIMIT
    override val maxIntervalsPerSession: Int =
        maxIntervalsPerSession() ?: ThreadBlockageConfig.DEFAULT_MAX_INTERVALS_PER_SESSION
    override val minDurationMs: Int = minDurationMs() ?: ThreadBlockageConfig.DEFAULT_MIN_DURATION_MS
}
