package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Resolved persistence config. A provider returning null means the default is used.
 */
class PersistenceConfig(
    multiFileEnabled: () -> Boolean? = { null },
) {
    val multiFileEnabled: Boolean by lazy { multiFileEnabled() ?: false }
}
