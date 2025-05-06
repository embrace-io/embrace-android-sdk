package io.embrace.android.embracesdk.internal.otel.sdk

import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.TraceId
import kotlin.random.Random

class IdGenerator(
    private val random: Random = Random.Default,
) {
    /**
     * Generate a valid W3C-compliant traceparent. See the format here: https://www.w3.org/TR/trace-context/#traceparent-header-field-values
     *
     * Note: because Embrace may be recording a span on our side for the given traceparent, we have set the "sampled" flag to indicate that.
     */
    fun generateTraceparent(): String =
        "00-" + TraceId.fromLongs(
            validRandomLong(),
            validRandomLong()
        ) + "-" + SpanId.fromLong(validRandomLong()) + "-01"

    fun generateUUID(): String = SpanId.fromLong(validRandomLong())

    private fun validRandomLong(): Long {
        var value: Long
        do {
            value = random.nextLong()
        } while (value == 0L)
        return value
    }

    companion object {
        private val INSTANCE = IdGenerator()

        fun generateW3CTraceparent(): String = INSTANCE.generateTraceparent()

        fun generateLaunchInstanceId(): String = INSTANCE.generateUUID()
    }
}
