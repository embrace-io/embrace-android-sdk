package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Resolved background activity config.
 */
interface BackgroundActivityConfig {
    val captureEnabled: Boolean
}

/**
 * Creates a [BackgroundActivityConfig]. A provider returning null means the default is used.
 */
inline fun BackgroundActivityConfig(
    crossinline captureEnabled: () -> Boolean? = { null },
): BackgroundActivityConfig = object : BackgroundActivityConfig {
    override val captureEnabled: Boolean = captureEnabled() ?: false
}
