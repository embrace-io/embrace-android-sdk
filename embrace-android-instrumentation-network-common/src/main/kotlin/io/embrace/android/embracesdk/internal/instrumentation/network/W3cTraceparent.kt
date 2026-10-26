package io.embrace.android.embracesdk.internal.instrumentation.network

/**
 * Parses version 00 W3C traceparents: 00-<32 hex trace id>-<16 hex span id>-<2 hex flags>.
 */
object W3cTraceparent {
    internal const val TRACE_ID_START = 3
    internal const val TRACE_ID_END = 35
    internal const val SPAN_ID_START = 36
    internal const val SPAN_ID_END = 52
    internal const val FLAGS_START = 53
    private const val LENGTH = 55

    /**
     * Returns true if [value] is a well-formed version 00 traceparent with non-zero trace and span IDs.
     */
    @JvmStatic
    fun isValid(value: String): Boolean =
        value.length == LENGTH &&
            value[0] == '0' &&
            value[1] == '0' &&
            value[2] == '-' &&
            value[TRACE_ID_END] == '-' &&
            value[SPAN_ID_END] == '-' &&
            isLowercaseHex(value, TRACE_ID_START, TRACE_ID_END, requireNonZero = true) &&
            isLowercaseHex(value, SPAN_ID_START, SPAN_ID_END, requireNonZero = true) &&
            isLowercaseHex(value, FLAGS_START, LENGTH, requireNonZero = false)

    /**
     * Returns true if both traceparents have the same trace ID.
     */
    @JvmStatic
    fun isSameTrace(a: String, b: String): Boolean =
        a.length == LENGTH &&
            b.length == LENGTH &&
            a.regionMatches(TRACE_ID_START, b, TRACE_ID_START, TRACE_ID_END - TRACE_ID_START)

    private fun isLowercaseHex(value: String, start: Int, end: Int, requireNonZero: Boolean): Boolean {
        var nonZero = !requireNonZero
        for (i in start until end) {
            val c = value[i]
            if (c !in '0'..'9' && c !in 'a'..'f') {
                return false
            }
            if (c != '0') {
                nonZero = true
            }
        }
        return nonZero
    }
}
