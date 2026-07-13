package io.embrace.android.embracesdk.internal.utils

import java.util.regex.Pattern

/**
 * Holds a collection of regex patterns defining redaction rules for strings such as URLs. If a string matches any of the given patterns
 * it is redacted, either by being replaced by the [redactionLabel] or by having each of its capturing groups replaced by the
 * [redactionLabel].
 */
class RedactionPatterns(
    val patterns: List<Pattern>,
    val redactionLabel: String = "<redacted>",
) {
    /**
     * Redact the given value if it matches any of the patterns listed. If `value` does not match any of the [patterns] the string is
     * returned unchanged.
     */
    fun redacted(value: String): String {
        if (patterns.isEmpty()) {
            return value
        }

        for (p in patterns) {
            val matcher = p.matcher(value)
            if (matcher.matches()) {
                if (matcher.groupCount() == 0) {
                    return redactionLabel
                }

                val builder = StringBuilder(value)
                for (i in matcher.groupCount() downTo 1) {
                    val start = matcher.start(i)
                    val end = matcher.end(i)
                    if (start < 0) {
                        continue
                    }
                    builder.replace(start, end, redactionLabel)
                }

                return builder.toString()
            }
        }

        return value
    }
}
