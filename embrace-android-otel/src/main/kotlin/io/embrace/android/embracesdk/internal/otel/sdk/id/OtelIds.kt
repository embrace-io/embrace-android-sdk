package io.embrace.android.embracesdk.internal.otel.sdk.id

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaIdGenerator
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanId

object OtelIds {

    private val generator = OtelJavaIdGenerator.random()

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
    const val INVALID_SPAN_ID: String = "0000000000000000"
}
