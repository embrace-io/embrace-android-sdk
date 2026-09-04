package io.embrace.android.embracesdk.internal.instrumentation.webview

/**
 * Drops the values from any `key=value` pairs in a URL fragment, and drops any long unstructured
 * segment, while leaving hash routes and short anchors intact.
 *
 * Must produce byte-identical output to the iOS implementation, so treat a change here as a change
 * to both platforms. Operates on the raw percent-encoded string so that an encoded `%26` is not
 * mistaken for a separator, and emits each separator as it was found so that `;` is not rewritten
 * to `&`. Lengths are measured in UTF-16 code units.
 */
internal object UrlFragmentRedactor {

    private const val MAX_KEY_LENGTH = 64
    private const val OPAQUE_SEGMENT_THRESHOLD = 64
    private const val MAX_RECURSION_DEPTH = 4
    private const val PAIR_SEPARATORS = "&;"

    // a denylist rather than an allowlist, as real parameter names include `mids[]` and `filter:name`
    private val DISALLOWED_KEY_CHARS = charArrayOf('/', '?', '#', '&', '=')
    private const val SPACE_CODE = 0x20
    private val WHITESPACE_CONTROL_CODES = 0x09..0x0D

    /**
     * Redacts [fragment], which is the part of a URL after the '#' with the '#' itself removed.
     */
    fun redact(fragment: String): String = redactPairs(fragment, depth = 0)

    private fun redactPairs(text: String, depth: Int): String {
        val result = StringBuilder(text.length)
        var segmentStart = 0

        text.forEachIndexed { index, char ->
            if (PAIR_SEPARATORS.contains(char)) {
                result.append(redactSegment(text.substring(segmentStart, index), depth))
                result.append(char)
                segmentStart = index + 1
            }
        }
        result.append(redactSegment(text.substring(segmentStart), depth))
        return result.toString()
    }

    /**
     * Applies the segment rules in order, first match wins.
     */
    private fun redactSegment(segment: String, depth: Int): String {
        // a key/value pair: keep the name and the '=', drop the value
        val equalsOffset = segment.indexOf('=')
        if (equalsOffset > 0 && isPlausibleKey(segment, equalsOffset)) {
            return segment.substring(0, equalsOffset + 1)
        }

        // a route: keep the path and recurse past an embedded '?'. Exceeding the recursion bound
        // falls through to the length rule below.
        val firstChar = segment.firstOrNull()
        if ((firstChar == '/' || firstChar == '!') && depth < MAX_RECURSION_DEPTH) {
            val queryOffset = segment.indexOf('?')
            if (queryOffset < 0) {
                return segment
            }
            val route = segment.substring(0, queryOffset + 1)
            return route + redactPairs(segment.substring(queryOffset + 1), depth + 1)
        }

        // no recognisable structure, so judge on length alone
        return when {
            segment.length <= OPAQUE_SEGMENT_THRESHOLD -> segment
            else -> ""
        }
    }

    private fun isPlausibleKey(segment: String, equalsOffset: Int): Boolean {
        if (equalsOffset > MAX_KEY_LENGTH) {
            return false
        }
        for (i in 0 until equalsOffset) {
            if (isDisallowedInKey(segment[i])) {
                return false
            }
        }
        return true
    }

    private fun isDisallowedInKey(char: Char): Boolean = char in DISALLOWED_KEY_CHARS ||
        char.code == SPACE_CODE ||
        char.code in WHITESPACE_CONTROL_CODES
}
