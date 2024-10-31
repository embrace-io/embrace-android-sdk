package io.embrace.android.embracesdk.internal.opentelemetry

import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanBuilder
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer

/**
 * Creates a new [SpanBuilder] that marks the resulting span as private if [internal] is true
 */
internal fun Tracer.embraceSpanBuilder(
    name: String,
    type: TelemetryType,
    internal: Boolean,
    private: Boolean,
    parent: EmbraceSpan? = null,
): EmbraceSpanBuilder = EmbraceSpanBuilder(
    tracer = this,
    name = name,
    telemetryType = type,
    internal = internal,
    private = private,
    parentSpan = parent,
)
