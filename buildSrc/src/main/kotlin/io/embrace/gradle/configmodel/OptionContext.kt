package io.embrace.gradle.configmodel

/**
 * What the options of a config schema can refer to, and the models their fields are declared into.
 */
internal class OptionContext(
    val enums: Map<String, ConfigEnumSpec>,
    val remote: ModelBuilder,
    val local: ModelBuilder,
    val remoteFields: FieldParser,
    val localFields: FieldParser,
    val instrumented: Map<String, InstrumentedClassSpec>,
) {
    /** Options may hold remote classes, such as a set of network capture rules, but not local ones. */
    val optionTypes: Set<String> get() = enums.keys + remote.classNames

    val remoteTypes: Set<String> get() = remote.classNames

    val localTypes: Set<String> get() = local.classNames + enums.keys
}
