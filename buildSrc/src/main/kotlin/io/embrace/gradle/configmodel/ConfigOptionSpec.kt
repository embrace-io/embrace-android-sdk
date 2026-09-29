package io.embrace.gradle.configmodel

/**
 * A config option. Its value is the [remote] value, then the [local] value, then [default], unless a hand-written
 * [resolver] computes it instead.
 *
 * [localPath] is where the option is read from in embrace-config.json. On Android the local value reaches the SDK
 * through [instrumented], and [pluginValue] replaces the value the Gradle plugin reads from [localPath].
 */
data class ConfigOptionSpec(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val default: Any?,
    val doc: String?,
    val remote: RemoteSourceSpec?,
    val localPath: String?,
    val instrumented: InstrumentedMethodSpec?,
    val pluginValue: String?,
    val resolver: String?,
) {
    val kotlinType: String get() = if (nullable) "$type?" else type
}
