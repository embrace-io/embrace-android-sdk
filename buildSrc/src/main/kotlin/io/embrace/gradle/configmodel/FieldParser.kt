package io.embrace.gradle.configmodel

/**
 * Parses the declaration of a JSON model field. A remote field has KDoc; a local field has a user-facing
 * description, which is its KDoc and its description in embrace-config-schema.json.
 */
internal class FieldParser(
    private val reader: SpecReader,
    private val local: Boolean,
    private val enums: Map<String, ConfigEnumSpec>,
) {

    companion object {
        private val REMOTE_KEYS = listOf("name", "type", "doc", "deprecated")
        private val LOCAL_KEYS = listOf("name", "type", "description", "internal", "schema_default", "min_length", "max_length")
        private val VALUE_KEYS = listOf("required", "nullable", "default")

        /** The Kotlin name of a field, which is the camelCase form of its JSON key unless it sets `name`. */
        fun defaultName(key: String): String = key.split('_').mapIndexed { index, part ->
            if (index == 0) part else part.replaceFirstChar { it.uppercaseChar() }
        }.joinToString("")
    }

    /** The keys that declare a field where an option reads it. */
    val sourceKeys: List<String> get() = if (local) LOCAL_KEYS else REMOTE_KEYS

    /** The keys of a field declared on its own. */
    val fieldKeys: List<String> get() = listOf("key") + sourceKeys + VALUE_KEYS

    /**
     * Parses the field declared by [map] with the JSON key [key]. [defaultType], [defaultDoc] and [defaultSchemaDefault]
     * apply when an option declares the field and [map] leaves them out.
     */
    @Suppress("LongParameterList")
    fun parse(
        map: Map<String, Any?>,
        key: String,
        location: String,
        types: Set<String>,
        defaultType: String? = null,
        defaultDoc: String? = null,
        defaultSchemaDefault: Any? = null,
    ): ConfigFieldSpec {
        val type = map["type"]?.let { reader.string(it, "$location.type") } ?: defaultType
            ?: reader.fail(location, "type is required")
        val element = ConfigTypes.element(type)
        reader.ensure(element in ConfigTypes.SCALARS || element in types, location) { ConfigTypes.unsupportedTypeMessage(type) }

        val default = map["default"]
        reader.ensure(default == null || ConfigTypes.isValidDefault(default, type, enums), location) {
            "default '$default' is not a valid $type"
        }
        val required = reader.boolean(map["required"], "$location.required") ?: false
        reader.ensure(!required || default == null, location) { "a field can't be both required and have a default" }
        val nullable = reader.boolean(map["nullable"], "$location.nullable") ?: (!required && default == null)
        reader.ensure(nullable || required || default != null, location) {
            "a non-null field must be required or have a default"
        }

        val description = map["description"]?.let { reader.string(it, "$location.description") }
        val schemaDefault = map["schema_default"] ?: defaultSchemaDefault
        reader.ensure(schemaDefault == null || ConfigTypes.isValidDefault(schemaDefault, type, enums), location) {
            "schema_default '$schemaDefault' is not a valid $type"
        }
        val (minLength, maxLength) = listOf("min_length", "max_length").map { lengthKey ->
            map[lengthKey]?.let {
                reader.ensure(it is Int && it >= 0 && type == "String", location) {
                    "$lengthKey must be a non-negative Int on a String field"
                }
                it as Int
            }
        }
        return ConfigFieldSpec(
            key = key,
            name = map["name"]?.let { reader.string(it, "$location.name") } ?: defaultName(key),
            type = type,
            doc = if (local) description ?: defaultDoc else map["doc"]?.let { reader.string(it, "$location.doc") } ?: defaultDoc,
            required = required,
            default = default,
            nullable = nullable,
            deprecated = map["deprecated"]?.let { reader.string(it, "$location.deprecated") },
            description = description,
            internal = reader.boolean(map["internal"], "$location.internal") ?: false,
            schemaDefault = schemaDefault,
            minLength = minLength,
            maxLength = maxLength,
        )
    }
}
