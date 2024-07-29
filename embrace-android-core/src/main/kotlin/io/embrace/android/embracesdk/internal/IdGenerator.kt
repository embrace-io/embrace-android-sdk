package io.embrace.android.embracesdk.internal

import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.TraceId
import kotlin.random.Random

public class IdGenerator(
    private val random: Random = Random.Default
) {
    /**
     * Generate a valid W3C-compliant traceparent. See the format here: https://www.w3.org/TR/trace-context/#traceparent-header-field-values
     *
     * Note: because Embrace may be recording a span on our side for the given traceparent, we have set the "sampled" flag to indicate that.
     */
    public fun generateTraceparent(): String =
        "00-" + TraceId.fromLongs(validRandomLong(), validRandomLong()) + "-" + SpanId.fromLong(validRandomLong()) + "-01"

    public fun generateUUID(): String = SpanId.fromLong(validRandomLong())

    private fun validRandomLong(): Long {
        var value: Long
        do {
            value = random.nextLong()
        } while (value == 0L)
        return value
    }

    public companion object {
        private val INSTANCE = IdGenerator()

        @JvmStatic
        public fun generateW3CTraceparent(): String = INSTANCE.generateTraceparent()

        public fun generateLaunchInstanceId(): String = INSTANCE.generateUUID()
    }
}
