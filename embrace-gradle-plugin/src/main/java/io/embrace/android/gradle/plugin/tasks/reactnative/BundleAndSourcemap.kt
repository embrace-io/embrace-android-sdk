package io.embrace.android.gradle.plugin.tasks.reactnative

import kotlinx.serialization.Serializable
import java.io.Reader
import java.io.Writer

/**
 * The gzipped JSON payload uploaded for a React Native bundle.
 */
@Serializable
data class BundleAndSourcemap(
    val bundle: String,
    val sourcemap: String,
)

/**
 * Writes the [BundleAndSourcemap] JSON for [bundle] and [sourcemap], streaming their contents so neither is held in
 * memory. kotlinx.serialization can only encode a string value from a [String] so this workaround is required
 * to avoid reading the whole file into memory.
 */
internal fun writeBundleAndSourcemapJson(bundle: Reader, sourcemap: Reader, out: Writer) {
    out.write("{\"bundle\":")
    writeJsonString(bundle, out)
    out.write(",\"sourcemap\":")
    writeJsonString(sourcemap, out)
    out.write("}")
}

private fun writeJsonString(reader: Reader, out: Writer) {
    out.write('"'.code)
    val buffer = CharArray(DEFAULT_BUFFER_SIZE)
    var n: Int
    while (reader.read(buffer).also { n = it } != -1) {
        // write runs of characters that need no escaping in one call
        var runStart = 0
        for (i in 0 until n) {
            val escaped = escape(buffer[i]) ?: continue
            out.write(buffer, runStart, i - runStart)
            out.write(escaped)
            runStart = i + 1
        }
        out.write(buffer, runStart, n - runStart)
    }
    out.write('"'.code)
}

/**
 * Returns the JSON escape sequence for [c], or null if it can be written as is. Matches kotlinx.serialization.
 */
private fun escape(c: Char): String? = when {
    c == '"' -> "\\\""
    c == '\\' -> "\\\\"
    c >= ' ' -> null
    else -> when (c.code) {
        0x08 -> "\\b"
        0x09 -> "\\t"
        0x0A -> "\\n"
        0x0C -> "\\f"
        0x0D -> "\\r"
        else -> "\\u" + c.code.toString(16).padStart(4, '0')
    }
}
