package io.embrace.gradle.configmodel

/**
 * A parsed config schema file.
 */
data class ConfigSchema(
    val packages: ConfigPackages,
    val enums: List<ConfigEnumSpec>,
    val remote: ConfigModelSpec,
    val local: ConfigModelSpec,
    val instrumented: List<InstrumentedClassSpec>,
    val groups: List<ConfigGroupSpec>,
    val jsonSchema: JsonSchemaSpec,
) {
    val options: List<ConfigOptionSpec> get() = groups.flatMap { it.options }

    fun instrumentedMethods(cls: InstrumentedClassSpec): List<Pair<ConfigOptionSpec, InstrumentedMethodSpec>> =
        options.mapNotNull { option -> option.instrumented?.takeIf { it.className == cls.name }?.let { option to it } }
}
