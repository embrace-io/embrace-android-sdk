package io.embrace.gradle.configmodel

/**
 * The packages generated Android code is written to.
 */
data class ConfigPackages(
    val remote: String,
    val local: String,
    val instrumented: String,
    val instrumentedImpl: String,
    val resolved: String,
    val plugin: String,
    val pluginDsl: String,
)
