package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_STACKTRACE_SIZE_LIMIT

/**
 * Resolved thread blockage config. A provider returning null means the default is used.
 */
class ThreadBlockageConfig(
    captureEnabled: () -> Boolean? = { null },
    sampleIntervalMs: () -> Long? = { null },
    maxStacktracesPerInterval: () -> Int? = { null },
    stacktraceFrameLimit: () -> Int? = { null },
    maxIntervalsPerSession: () -> Int? = { null },
    minDurationMs: () -> Int? = { null },
) {
    val captureEnabled: Boolean by lazy { captureEnabled() ?: true }
    val sampleIntervalMs: Long by lazy { sampleIntervalMs() ?: DEFAULT_SAMPLE_INTERVAL_MS }
    val maxStacktracesPerInterval: Int by lazy { maxStacktracesPerInterval() ?: DEFAULT_MAX_STACKTRACES_PER_INTERVAL }
    val stacktraceFrameLimit: Int by lazy { stacktraceFrameLimit() ?: DEFAULT_STACKTRACE_SIZE_LIMIT }
    val maxIntervalsPerSession: Int by lazy { maxIntervalsPerSession() ?: DEFAULT_MAX_INTERVALS_PER_SESSION }
    val minDurationMs: Int by lazy { minDurationMs() ?: DEFAULT_MIN_DURATION_MS }

    companion object {
        const val DEFAULT_SAMPLE_INTERVAL_MS: Long = 100
        const val DEFAULT_MAX_STACKTRACES_PER_INTERVAL: Int = 80
        const val DEFAULT_MAX_INTERVALS_PER_SESSION: Int = 5
        const val DEFAULT_MIN_DURATION_MS: Int = 1000
    }
}
