package io.embrace.android.embracesdk.internal.otel.sdk.id

import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.trace.IdGenerator

object OtelIds {

    private val generator = IdGenerator.random()

    /**
     * Generates a new valid SpanId.
     */
    fun generateSpanId(): String = generator.generateSpanId()

    /**
     * Generates a new valid TraceId.
     */
    fun generateTraceId(): String = generator.generateTraceId()

    /**
     * An invalid SpanId.
     */
    val invalidSpanId: String = SpanId.getInvalid()
}
