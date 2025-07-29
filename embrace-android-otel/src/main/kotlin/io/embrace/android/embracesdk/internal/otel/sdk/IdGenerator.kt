package io.embrace.android.embracesdk.internal.otel.sdk

import kotlin.random.Random

class IdGenerator(
    private val random: Random = Random.Default,
) {

    /**
     * Generate a valid W3C-compliant traceparent. See the format
     * here: https://www.w3.org/TR/trace-context/#traceparent-header-field-values
     *
     * Note: because Embrace may be recording a span on our side for the given traceparent,
     * we have set the "sampled" flag to indicate that.
     */
    fun generateTraceparent(): String {
        val traceId = generateTraceId()
        val spanId = generateSpanId()
        return "00-$traceId-$spanId-01"
    }

    private fun generateTraceId(): String = buildString(32) {
        appendHex(validRandomLong())
        appendHex(validRandomLong())
    }

    internal fun generateSpanId(): String = buildString(16) {
        appendHex(validRandomLong())
    }

    private fun validRandomLong(): Long {
        var value: Long
        do {
            value = random.nextLong()
        } while (value == 0L)
        return value
    }

    private fun StringBuilder.appendHex(value: Long) {
        append(value.toULong().toString(16).padStart(16, '0'))
    }

    companion object {
        private val INSTANCE = IdGenerator()

        fun generateW3CTraceparent(): String = INSTANCE.generateTraceparent()

        fun generateLaunchInstanceId(): String = INSTANCE.generateSpanId()
    }
}
