package io.embrace.gradle.configmodel

/**
 * An enum shared by the models and options of a config schema.
 */
data class ConfigEnumSpec(
    val name: String,
    val doc: String?,
    val values: List<ConfigEnumValueSpec>,
)
