package io.embrace.android.embracesdk.internal.instrumentation.network

/**
 * A well-formed version 00 W3C traceparent: 00-<32 hex trace id>-<16 hex span id>-<2 hex flags>.
 */
@JvmInline
value class TraceParent private constructor(val value: String) {

    val traceId: String
        get() = value.substring(TRACE_ID_START, TRACE_ID_END)

    val spanId: String
        get() = value.substring(SPAN_ID_START, SPAN_ID_END)

    val flags: String
        get() = value.substring(FLAGS_START)

    /**
     * Returns true if [traceparent] has this traceparent's trace ID.
     */
    fun isSameTrace(traceparent: String): Boolean =
        traceparent.length == LENGTH &&
            value.regionMatches(TRACE_ID_START, traceparent, TRACE_ID_START, TRACE_ID_END - TRACE_ID_START)

    fun isSameTrace(traceParent: TraceParent): Boolean =
        isSameTrace(traceParent.value)

    /**
     * Returns this traceparent with the trace flags of [traceParent].
     */
    fun withFlagsOf(traceParent: TraceParent): TraceParent =
        TraceParent(value.substring(0, FLAGS_START) + traceParent.flags)

    override fun toString(): String = value

    companion object {
        private const val FLAGS_START = 53
        private const val TRACE_ID_START = 3
        private const val TRACE_ID_END = 35
        private const val SPAN_ID_START = 36
        private const val SPAN_ID_END = 52
        private const val LENGTH = 55

        /**
         * Returns [value] as a [TraceParent], or null if it isn't well-formed or has an all-zero trace or span ID.
         */
        fun parse(value: String): TraceParent? = if (isValid(value)) TraceParent(value) else null

        private fun isValid(value: String): Boolean =
            value.length == LENGTH &&
                value[0] == '0' &&
                value[1] == '0' &&
                value[2] == '-' &&
                value[TRACE_ID_END] == '-' &&
                value[SPAN_ID_END] == '-' &&
                isLowercaseHex(value, TRACE_ID_START, TRACE_ID_END, requireNonZero = true) &&
                isLowercaseHex(value, SPAN_ID_START, SPAN_ID_END, requireNonZero = true) &&
                isLowercaseHex(value, FLAGS_START, LENGTH, requireNonZero = false)

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
}
