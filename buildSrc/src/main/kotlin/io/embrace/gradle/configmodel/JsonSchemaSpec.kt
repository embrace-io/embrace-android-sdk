package io.embrace.gradle.configmodel

/**
 * The identity of the generated embrace-config-schema.json.
 */
data class JsonSchemaSpec(
    val id: String,
    val title: String,
    val description: String,
)
