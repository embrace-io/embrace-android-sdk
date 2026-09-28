package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Resolved persistence config.
 */
interface PersistenceConfig {
    val multiFileEnabled: Boolean
}

/**
 * Creates a [PersistenceConfig]. A provider returning null means the default is used.
 */
inline fun PersistenceConfig(
    crossinline multiFileEnabled: () -> Boolean? = { null },
): PersistenceConfig = object : PersistenceConfig {
    override val multiFileEnabled: Boolean = multiFileEnabled() ?: false
}
