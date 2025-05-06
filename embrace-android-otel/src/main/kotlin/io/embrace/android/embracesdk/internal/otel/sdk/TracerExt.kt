package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.otel.schema.TelemetryType
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanBuilderWrapper
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer

/**
 * Creates a new [SpanBuilder] that marks the resulting span as private if [internal] is true
 */
fun Tracer.otelSpanBuilderWrapper(
    name: String,
    type: TelemetryType,
    internal: Boolean,
    private: Boolean,
    autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    parent: EmbraceSpan? = null,
): OtelSpanBuilderWrapper = OtelSpanBuilderWrapper(
    tracer = this,
    name = name,
    telemetryType = type,
    internal = internal,
    private = private,
    autoTerminationMode = autoTerminationMode,
    parentSpan = parent,
)
