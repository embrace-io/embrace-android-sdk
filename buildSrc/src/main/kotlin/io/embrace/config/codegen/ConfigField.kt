package io.embrace.config.codegen

class ConfigField(
    val name: String,
    val type: ConfigType,
    val default: String,
    val local: LocalConfigProperty?,
    val remote: ConfigProperty?,
    val range: ConfigRange? = null,
) {
    val localGetter: String = (if (type == ConfigType.BOOLEAN) "is" else "get") + name.replaceFirstChar(Char::uppercaseChar)
}
