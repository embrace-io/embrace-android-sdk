package io.embrace.bytecode.config.local

/**
 * Getter on a local config class keyed by the `@LocalConfigKey` [path].
 */
data class LocalConfigGetter(
    val name: String,
    val path: String,
)
