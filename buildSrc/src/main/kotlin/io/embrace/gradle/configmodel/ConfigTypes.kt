package io.embrace.gradle.configmodel

/**
 * The types a config schema may use: a scalar, an enum or class defined in the schema, or a `List<T>`, `Set<T>` or
 * `Map<String, T>` of one. Types are written as Kotlin type expressions, without nullability.
 */
object ConfigTypes {

    val SCALARS: Set<String> = setOf("Boolean", "Int", "Long", "Float", "Double", "String")
    val NUMERIC: Set<String> = setOf("Int", "Long", "Float", "Double")
    private val TYPE = Regex("""(?:List|Set)<(\w+)>|Map<String, (\w+)>|(\w+)""")

    /** The scalar, enum or class that [type] holds, e.g. `Int` for `Set<Int>`, or null if [type] is malformed. */
    fun element(type: String): String? = TYPE.matchEntire(type)?.groupValues?.drop(1)?.single { it.isNotEmpty() }

    /** `List`, `Set` or `Map` for a collection, otherwise the type itself. */
    fun kind(type: String): String = type.substringBefore('<')

    fun isCollection(type: String): Boolean = kind(type) in setOf("List", "Set", "Map")

    fun unsupportedTypeMessage(type: String): String =
        "unsupported type '$type'. Use one of $SCALARS, an enum or class defined in this file, " +
            "or a List<T>, Set<T> or Map<String, T> of one"

    /** Collection defaults may only be empty, and an enum default is the name of one of its values. */
    fun isValidDefault(value: Any, type: String, enums: Map<String, ConfigEnumSpec>): Boolean = when (kind(type)) {
        "Int" -> value is Int
        "Long" -> value is Int || value is Long
        "Float", "Double" -> value is Number
        "Boolean" -> value is Boolean
        "String" -> value is String
        "List", "Set" -> value == emptyList<Any>()
        "Map" -> value == emptyMap<Any, Any>()
        in enums -> enums.getValue(type).values.any { it.name == value }
        else -> false
    }
}
