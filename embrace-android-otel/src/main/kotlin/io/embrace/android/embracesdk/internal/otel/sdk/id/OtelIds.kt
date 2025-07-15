package io.embrace.android.embracesdk.internal.otel.sdk.id

import io.embrace.opentelemetry.kotlin.k2j.id.TracingIdGeneratorImpl

object OtelIds {

    private val generator = TracingIdGeneratorImpl()

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
    val invalidSpanId: String = generator.invalidSpanId
}
