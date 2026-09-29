package io.embrace.gradle.configmodel

/**
 * The method of an [InstrumentedClassSpec] that delivers an option's local value to the SDK. Its body returns
 * [default] until the Gradle plugin rewrites it.
 */
data class InstrumentedMethodSpec(
    val className: String,
    val name: String,
    val type: String,
    val nullable: Boolean,
    val default: Any?,
) {
    val kotlinType: String get() = if (nullable) "$type?" else type
}
