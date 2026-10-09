package io.embrace.android.gradle.plugin.instrumentation.config.local

/**
 * A getter on a generated local config class, keyed by its `@LocalConfigKey` [path].
 */
data class LocalConfigGetter(
    val name: String,
    val path: String,
)
