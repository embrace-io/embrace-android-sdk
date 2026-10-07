package io.embrace.config.codegen

class ConfigField(
    val name: String,
    val type: ConfigType,
    val default: String,
    val local: ConfigProperty?,
    val remote: ConfigProperty?,
)
