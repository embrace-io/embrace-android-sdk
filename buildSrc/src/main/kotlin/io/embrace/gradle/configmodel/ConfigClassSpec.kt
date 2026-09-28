package io.embrace.gradle.configmodel

/**
 * A class in a config model file.
 */
data class ConfigClassSpec(
    val name: String,
    val doc: String?,
    val fields: List<ConfigFieldSpec>,
)
