package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Resolved log message config.
 */
interface LogConfig {
    val maxMessageLength: Int
    val infoLimit: Int
    val warnLimit: Int
    val errorLimit: Int

    companion object {
        const val DEFAULT_MAX_MESSAGE_LENGTH: Int = 128
        const val DEFAULT_INFO_LIMIT: Int = 100
        const val DEFAULT_WARN_LIMIT: Int = 200
        const val DEFAULT_ERROR_LIMIT: Int = 500
    }
}

/**
 * Creates a [LogConfig]. A provider returning null means the default is used.
 */
inline fun LogConfig(
    crossinline maxMessageLength: () -> Int? = { null },
    crossinline infoLimit: () -> Int? = { null },
    crossinline warnLimit: () -> Int? = { null },
    crossinline errorLimit: () -> Int? = { null },
): LogConfig = object : LogConfig {
    override val maxMessageLength: Int = maxMessageLength() ?: LogConfig.DEFAULT_MAX_MESSAGE_LENGTH
    override val infoLimit: Int = infoLimit() ?: LogConfig.DEFAULT_INFO_LIMIT
    override val warnLimit: Int = warnLimit() ?: LogConfig.DEFAULT_WARN_LIMIT
    override val errorLimit: Int = errorLimit() ?: LogConfig.DEFAULT_ERROR_LIMIT
}
