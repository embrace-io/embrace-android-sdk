package io.embrace.config.codegen

/**
 * A feature's local value. [json] is its dot-separated path in embrace-config.json.
 */
class LocalConfigProperty(
    val type: ConfigType,
    val json: String,
)
