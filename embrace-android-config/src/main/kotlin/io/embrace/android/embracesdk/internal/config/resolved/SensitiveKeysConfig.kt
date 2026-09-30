package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Resolved sensitive keys config.
 */
interface SensitiveKeysConfig {

    /**
     * Keys whose values should be redacted before they are sent to the server, or null if none are configured.
     */
    val denylist: List<String>?
}

/**
 * Creates a [SensitiveKeysConfig]. A provider returning null means the default is used.
 */
inline fun SensitiveKeysConfig(
    crossinline denylist: () -> List<String>? = { null },
): SensitiveKeysConfig = object : SensitiveKeysConfig {
    override val denylist: List<String>? = denylist()
}
