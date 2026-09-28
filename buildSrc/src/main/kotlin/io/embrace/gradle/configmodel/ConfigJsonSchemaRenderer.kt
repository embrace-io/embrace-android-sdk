package io.embrace.gradle.configmodel

/**
 * Renders embrace-config-schema.json, the published JSON Schema for embrace-config.json, from the local model. Its
 * internal fields are left out, and so is any object with nothing else in it.
 */
class ConfigJsonSchemaRenderer(private val schema: ConfigSchema) {

    private val classes = schema.local.classes.associateBy { it.name }
    private val enums = schema.enums.associateBy { it.name }

    fun render(): String {
        val document = linkedMapOf(
            "\$schema" to "http://json-schema.org/draft-04/schema#",
            "description" to schema.jsonSchema.description,
            "id" to schema.jsonSchema.id,
            "properties" to properties(schema.local.root),
            "title" to schema.jsonSchema.title,
            "type" to "object",
        )
        return JsonWriter.write(document) + "\n"
    }

    private fun properties(cls: ConfigClassSpec): Map<String, Any> =
        cls.fields.filterNot { it.internal }.mapNotNull { field -> fieldSchema(field)?.let { field.key to it } }.toMap(LinkedHashMap())

    private fun fieldSchema(field: ConfigFieldSpec): Map<String, Any>? {
        classes[field.type]?.let { return objectSchema(it, field.description) }
        val element = checkNotNull(ConfigTypes.element(field.type))
        return when (ConfigTypes.kind(field.type)) {
            "List", "Set" -> {
                val items = classes[element]?.let { objectSchema(it, null) } ?: mapOf("type" to jsonType(element))
                describe(field.description) + linkedMapOf("type" to "array", "items" to items, "minItems" to 1, "uniqueItems" to true)
            }
            else -> describe(field.description) + listOfNotNull(
                field.schemaDefault?.let { "default" to jsonValue(it, field.type) },
                "type" to jsonType(field.type),
                field.minLength?.let { "minLength" to it },
                field.maxLength?.let { "maxLength" to it },
            )
        }
    }

    private fun objectSchema(cls: ConfigClassSpec, description: String?): Map<String, Any>? {
        val properties = properties(cls).ifEmpty { return null }
        return describe(description) + linkedMapOf("type" to "object", "properties" to properties, "minProperties" to 1)
    }

    private fun describe(description: String?): Map<String, Any> =
        description?.let { linkedMapOf("description" to it.trim()) } ?: linkedMapOf()

    private fun jsonType(type: String): String = when (ConfigTypes.kind(type)) {
        "Boolean" -> "boolean"
        "Int", "Long" -> "integer"
        "Float", "Double" -> "number"
        "Map" -> "object"
        else -> "string"
    }

    /** An enum default is documented by its JSON name. */
    private fun jsonValue(value: Any, type: String): Any =
        enums[type]?.values?.single { it.name == value }?.json ?: value
}
