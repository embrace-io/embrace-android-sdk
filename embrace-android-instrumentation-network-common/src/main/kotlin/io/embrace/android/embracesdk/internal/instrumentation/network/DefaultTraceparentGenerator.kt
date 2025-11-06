package io.embrace.android.embracesdk.internal.instrumentation.network

import kotlin.random.Random

object DefaultTraceparentGenerator : TraceparentGenerator {

    override fun generateW3cTraceparent(): String {
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
            value = Random.nextLong()
        } while (value == 0L)
        return value
    }

    private fun StringBuilder.appendHex(value: Long) {
        append(value.toULong().toString(16).padStart(16, '0'))
    }
}
