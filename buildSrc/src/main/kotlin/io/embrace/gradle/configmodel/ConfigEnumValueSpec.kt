package io.embrace.gradle.configmodel

/**
 * A value of a [ConfigEnumSpec]. [json] is how it is written in JSON.
 */
data class ConfigEnumValueSpec(
    val name: String,
    val json: String,
    val doc: String?,
)
