package io.embrace.gradle.configmodel

/**
 * An SDK interface whose method bodies the Gradle plugin rewrites with values from embrace-config.json.
 * [property] is its name on `InstrumentedConfig`, and [pluginParams] are the values the plugin needs to compute it.
 */
data class InstrumentedClassSpec(
    val name: String,
    val property: String,
    val doc: String?,
    val pluginParams: List<String>,
)
