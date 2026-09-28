package io.embrace.gradle.configmodel

/**
 * Reads values out of parsed YAML, failing with the location of anything that isn't the expected shape.
 */
class SpecReader(val sourceName: String) {

    @Suppress("UNCHECKED_CAST")
    fun map(value: Any?, location: String, vararg allowed: String): Map<String, Any?> {
        val map = (value as? Map<*, *>)?.takeIf { it.keys.all { key -> key is String } } as? Map<String, Any?>
            ?: fail(location, "expected a mapping")
        val unknown = map.keys - allowed.toSet()
        ensure(unknown.isEmpty(), location) { "unknown key(s) $unknown. Allowed keys are ${allowed.toList()}" }
        return map
    }

    @Suppress("UNCHECKED_CAST")
    fun list(value: Any?, location: String): List<Any?> =
        value as? List<Any?> ?: fail(location, "expected a list")

    fun string(value: Any?, location: String): String =
        (value as? String)?.takeIf { it.isNotBlank() } ?: fail(location, "expected a non-empty string")

    fun boolean(value: Any?, location: String): Boolean? =
        if (value == null || value is Boolean) value as Boolean? else fail(location, "expected true or false")

    fun ensureUnique(values: List<String>, location: String, what: String) {
        values.groupBy { it }.filter { it.value.size > 1 }.keys.firstOrNull()?.let {
            fail(location, "$what '$it' is used more than once")
        }
    }

    inline fun ensure(condition: Boolean, location: String, message: () -> String) {
        if (!condition) {
            fail(location, message())
        }
    }

    fun fail(location: String, message: String): Nothing =
        throw IllegalArgumentException("$location: $message")
}
