package io.embrace.gradle.configmodel

/**
 * A feature's options, generated as a slice of `EmbraceConfig`.
 */
data class ConfigGroupSpec(
    val name: String,
    val doc: String?,
    val options: List<ConfigOptionSpec>,
) {
    val className: String get() = name.replaceFirstChar { it.uppercaseChar() } + "Config"
}
