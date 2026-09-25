package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Resolved AppExitInfo config. A provider returning null means the default is used.
 */
class AeiConfig(
    captureEnabled: () -> Boolean? = { null },
    traceMaxLimit: () -> Int? = { null },
    maxNum: () -> Int? = { null },
) {
    val captureEnabled: Boolean by lazy { captureEnabled() ?: true }

    /**
     * Max size in bytes of captured ndk/anr traces.
     */
    val traceMaxLimit: Int by lazy { traceMaxLimit() ?: DEFAULT_TRACE_MAX_LIMIT }

    /**
     * Max number of AEI records to capture. 0 means no limit.
     */
    val maxNum: Int by lazy { maxNum() ?: DEFAULT_MAX_NUM }

    companion object {
        const val DEFAULT_TRACE_MAX_LIMIT: Int = 10485760 // 10MB
        const val DEFAULT_MAX_NUM: Int = 0
    }
}
