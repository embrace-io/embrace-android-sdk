package io.embrace.gradle.configmodel

/**
 * Writes maps, lists, strings, numbers and booleans as pretty-printed JSON, keeping the order of map entries.
 */
object JsonWriter {

    fun write(value: Any): String = buildString { append(value, "") }

    private fun StringBuilder.append(value: Any, indent: String) {
        when (value) {
            is Map<*, *> -> block("{", "}", value.entries.toList(), indent) { (key, entry), inner ->
                append(quoted(key as String)).append(": ")
                append(checkNotNull(entry), inner)
            }
            is List<*> -> block("[", "]", value, indent) { entry, inner -> append(checkNotNull(entry), inner) }
            is String -> append(quoted(value))
            is Number, is Boolean -> append(value.toString())
            else -> error("Can't write $value as JSON")
        }
    }

    private fun <T> StringBuilder.block(
        open: String,
        close: String,
        entries: List<T>,
        indent: String,
        appendEntry: StringBuilder.(T, String) -> Unit,
    ) {
        if (entries.isEmpty()) {
            append(open).append(close)
            return
        }
        val inner = "$indent  "
        append(open).append('\n')
        entries.forEachIndexed { index, entry ->
            append(inner)
            appendEntry(entry, inner)
            append(if (index < entries.lastIndex) ",\n" else "\n")
        }
        append(indent).append(close)
    }

    private fun quoted(value: String): String = buildString {
        append('"')
        value.forEach {
            when (it) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> if (it < ' ') append("\\u%04x".format(it.code)) else append(it)
            }
        }
        append('"')
    }
}
