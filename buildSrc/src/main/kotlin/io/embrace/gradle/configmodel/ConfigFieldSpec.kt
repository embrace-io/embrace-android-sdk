package io.embrace.gradle.configmodel

/**
 * A field of a JSON model class. [default] is the value as parsed from YAML, not yet converted to [type].
 *
 * The remaining properties only describe embrace-config.json fields in embrace-config-schema.json: an [internal]
 * field is left out of it, and [schemaDefault] is the default it documents.
 */
data class ConfigFieldSpec(
    val key: String,
    val name: String,
    val type: String,
    val doc: String?,
    val required: Boolean,
    val default: Any?,
    val nullable: Boolean,
    val deprecated: String?,
    val description: String? = null,
    val internal: Boolean = false,
    val schemaDefault: Any? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
)
