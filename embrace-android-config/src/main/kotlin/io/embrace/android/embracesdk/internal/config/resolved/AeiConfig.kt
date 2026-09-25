package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Resolved AppExitInfo config.
 */
interface AeiConfig {
    val captureEnabled: Boolean

    /**
     * Max size in bytes of captured ndk/anr traces.
     */
    val traceMaxLimit: Int

    /**
     * Max number of AEI records to capture. 0 means no limit.
     */
    val maxNum: Int

    companion object {
        const val DEFAULT_TRACE_MAX_LIMIT: Int = 10485760 // 10MB
        const val DEFAULT_MAX_NUM: Int = 0
    }
}

/**
 * Creates an [AeiConfig]. A provider returning null means the default is used.
 */
inline fun AeiConfig(
    crossinline captureEnabled: () -> Boolean? = { null },
    crossinline traceMaxLimit: () -> Int? = { null },
    crossinline maxNum: () -> Int? = { null },
): AeiConfig = object : AeiConfig {
    override val captureEnabled: Boolean = captureEnabled() ?: true
    override val traceMaxLimit: Int = traceMaxLimit() ?: AeiConfig.DEFAULT_TRACE_MAX_LIMIT
    override val maxNum: Int = maxNum() ?: AeiConfig.DEFAULT_MAX_NUM
}
