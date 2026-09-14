package io.embrace.analysis.common.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Loose, Python-`dict.get`-flavoured access to JSON records plus `json.dumps` rendering.
 *
 * The store, corpus and plan files are read as plain dicts with `.get(...) or
 * default` everywhere, so malformed records (the salvaged 8.3.0 sweep rows with null versions,
 * empty profiles) flow through and print `?`. Typed schemas exist for writing; reading for a
 * REPORT keeps that tolerance, and these helpers make that explicit rather than sprinkling
 * casts through every report.
 */
object PyJson {

    /** `rec.get(key) or default`: missing, null and the empty string all yield the default. */
    fun str(obj: JsonObject?, key: String, default: String): String {
        val el = obj?.get(key)
        if (el == null || el is JsonNull) return default
        val content = if (el is JsonPrimitive) el.content else dumps(el)
        return content.ifEmpty { default }
    }

    /** `rec.get(key)` as a string, or null; non-primitives are rendered as `json.dumps` would. */
    fun strOrNull(obj: JsonObject?, key: String): String? {
        val el = obj?.get(key)
        if (el == null || el is JsonNull) return null
        return if (el is JsonPrimitive) el.content else dumps(el)
    }

    fun obj(obj: JsonObject?, key: String): JsonObject? = obj?.get(key) as? JsonObject

    fun arr(obj: JsonObject?, key: String): JsonArray? = obj?.get(key) as? JsonArray

    fun double(obj: JsonObject?, key: String): Double? =
        (obj?.get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }?.content?.toDoubleOrNull()

    /**
     * Python truthiness of a field: absent, `null`, `false`, zero, and an empty string, array or object
     * are false; everything else is true. A JSON boolean is handled explicitly - its `content` is the text
     * `true`/`false`, which is not a number, so reading it as one would call `true` falsy.
     */
    fun truthy(obj: JsonObject?, key: String): Boolean {
        val el = obj?.get(key) ?: return false
        return when (el) {
            is JsonNull -> false
            is JsonPrimitive -> when {
                el.isString -> el.content.isNotEmpty()
                el.content == "true" -> true
                el.content == "false" -> false
                else -> (el.content.toDoubleOrNull() ?: 0.0) != 0.0
            }
            is JsonArray -> el.isNotEmpty()
            is JsonObject -> el.isNotEmpty()
        }
    }

    /** `bool(rec.get(key, default))` for a boolean field. */
    fun bool(obj: JsonObject?, key: String, default: Boolean): Boolean {
        val el = obj?.get(key) ?: return default
        if (el is JsonNull) return false
        return when (el.jsonPrimitive.content) {
            "true" -> true
            "false" -> false
            else -> truthy(obj, key)
        }
    }

    /**
     * `json.dumps(el, sort_keys=sortKeys)` with the default separators (`, ` and `: `) and
     * `ensure_ascii=True`. Numbers are emitted with the literal text they were parsed from, which is
     * what Python does for ints and, for the floats that occur in these files, what `repr` gives.
     */
    fun dumps(el: JsonElement, sortKeys: Boolean = true): String = when (el) {
        is JsonNull -> "null"
        is JsonPrimitive -> if (el.isString) quote(el.content) else el.content
        is JsonArray -> el.joinToString(", ", "[", "]") { dumps(it, sortKeys) }
        is JsonObject -> {
            val entries = if (sortKeys) el.entries.sortedBy { it.key } else el.entries.toList()
            entries.joinToString(", ", "{", "}") { "${quote(it.key)}: ${dumps(it.value, sortKeys)}" }
        }
    }

    /**
     * `json.dumps(el, indent=indent, sort_keys=sortKeys)`: Python's pretty layout - one entry per line,
     * `", "` collapsed to a bare comma at line end, `": "` between key and value, empty containers as
     * `[]` / `{}` - with `ensure_ascii=True`. Number literals are emitted verbatim, so a file written
     * this way round-trips byte for byte.
     */
    fun dumpsPretty(el: JsonElement, indent: Int = 2, sortKeys: Boolean = true, level: Int = 0): String = when (el) {
        is JsonNull -> "null"
        is JsonPrimitive -> if (el.isString) quote(el.content) else el.content
        is JsonArray -> if (el.isEmpty()) {
            "[]"
        } else {
            val pad = " ".repeat(indent * (level + 1))
            el.joinToString(",\n", "[\n", "\n" + " ".repeat(indent * level) + "]") {
                pad + dumpsPretty(it, indent, sortKeys, level + 1)
            }
        }
        is JsonObject -> if (el.isEmpty()) {
            "{}"
        } else {
            val pad = " ".repeat(indent * (level + 1))
            val entries = if (sortKeys) el.entries.sortedBy { it.key } else el.entries.toList()
            entries.joinToString(",\n", "{\n", "\n" + " ".repeat(indent * level) + "}") {
                "$pad${quote(it.key)}: ${dumpsPretty(it.value, indent, sortKeys, level + 1)}"
            }
        }
    }

    /** Python's `str(list_of_str)` / `repr` of a string list: `['a', 'b']`. */
    fun reprList(items: List<String>): String = items.joinToString(", ", "[", "]") { repr(it) }

    /** Python `repr(str)`: single quotes unless the text has a single quote and no double quote. */
    fun repr(s: String): String {
        val useDouble = '\'' in s && '"' !in s
        val q = if (useDouble) '"' else '\''
        val body = StringBuilder()
        s.forEach { c ->
            when {
                c == '\\' -> body.append("\\\\")
                c == q -> body.append('\\').append(c)
                c == '\n' -> body.append("\\n")
                c == '\t' -> body.append("\\t")
                c == '\r' -> body.append("\\r")
                else -> body.append(c)
            }
        }
        return "$q$body$q"
    }

    private fun quote(s: String): String {
        val out = StringBuilder("\"")
        s.forEach { c ->
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c == '\b' -> out.append("\\b")
                c == '' -> out.append("\\f")
                c.code < ASCII_PRINTABLE_MIN || c.code > ASCII_MAX -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        return out.append('"').toString()
    }

    private const val ASCII_PRINTABLE_MIN = 0x20
    private const val ASCII_MAX = 0x7e
}
