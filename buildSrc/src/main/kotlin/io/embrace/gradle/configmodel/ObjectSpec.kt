package io.embrace.gradle.configmodel

/**
 * A JSON object that holds fields of several features, generated as its own class. [doc] documents the field that
 * holds it and [classDoc] the class itself.
 */
internal data class ObjectSpec(
    val key: String,
    val className: String,
    val property: String?,
    val doc: String?,
    val classDoc: String?,
    val deprecated: String?,
) {
    /** The field of the parent class that holds this object, whose JSON key is [key]'s last segment. */
    fun field(jsonKey: String): ConfigFieldSpec = ConfigFieldSpec(
        key = jsonKey,
        name = property ?: FieldParser.defaultName(jsonKey),
        type = className,
        doc = doc,
        required = false,
        default = null,
        nullable = true,
        deprecated = deprecated,
    )
}
